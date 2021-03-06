package com.hedvig.memberservice.services.signing

import com.hedvig.integration.botService.BotService
import com.hedvig.integration.botService.dto.UpdateUserContextDTO
import com.hedvig.integration.underwriter.UnderwriterApi
import com.hedvig.integration.underwriter.dtos.QuoteToSignStatusDto
import com.hedvig.integration.underwriter.dtos.SignMethod
import com.hedvig.memberservice.commands.SignMemberFromUnderwriterCommand
import com.hedvig.memberservice.commands.UpdateSwedishWebOnBoardingInfoCommand
import com.hedvig.memberservice.jobs.ContractsCreatedCollector
import com.hedvig.memberservice.query.MemberRepository
import com.hedvig.memberservice.query.SignedMemberRepository
import com.hedvig.memberservice.query.UnderwriterSignSessionRepository
import com.hedvig.memberservice.services.MemberHasExistingInsuranceException
import com.hedvig.memberservice.services.signing.sweden.SwedishBankIdSigningService
import com.hedvig.memberservice.services.signing.zignsec.ZignSecSigningService
import com.hedvig.memberservice.services.member.CannotSignInsuranceException
import com.hedvig.memberservice.services.member.dto.MemberSignResponse
import com.hedvig.memberservice.services.member.dto.MemberSignUnderwriterQuoteResponse
import com.hedvig.memberservice.services.redispublisher.RedisEventPublisher
import com.hedvig.memberservice.services.signing.simple.SimpleSigningService
import com.hedvig.memberservice.services.signing.underwriter.strategy.SignStrategy
import com.hedvig.memberservice.util.logger
import com.hedvig.memberservice.web.dto.IsMemberAlreadySignedResponse
import com.hedvig.memberservice.web.dto.IsSsnAlreadySignedMemberResponse
import com.hedvig.memberservice.web.v2.dto.SignStatusResponse
import com.hedvig.memberservice.web.v2.dto.UnderwriterQuoteSignRequest
import com.hedvig.memberservice.web.v2.dto.WebsignRequest
import org.axonframework.commandhandling.gateway.CommandGateway
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.lang.NonNull
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException
import javax.transaction.Transactional

@Service
class SigningService(
    private val underwriterApi: UnderwriterApi,
    private val signedMemberRepository: SignedMemberRepository,
    private val botService: BotService,
    private val memberRepository: MemberRepository,
    private val commandGateway: CommandGateway,
    private val swedishBankIdSigningService: SwedishBankIdSigningService,
    private val zignSecSigningService: ZignSecSigningService,
    private val simpleSigningService: SimpleSigningService,
    private val redisEventPublisher: RedisEventPublisher,
    private val underwriterSignSessionRepository: UnderwriterSignSessionRepository,
    private val scheduler: Scheduler
) {

    @Deprecated("Sign through underwriter instead")
    @Transactional
    fun startWebSign(memberId: Long, request: WebsignRequest): MemberSignResponse {

        val existing = signedMemberRepository.findBySsn(request.ssn)

        if (existing.isPresent) {
            throw MemberHasExistingInsuranceException()
        }

        when (val quote = underwriterApi.hasQuoteToSign(memberId.toString())) {
            is QuoteToSignStatusDto.EligibleToSign -> {
                val cmd = UpdateSwedishWebOnBoardingInfoCommand(memberId, request.ssn, request.email)
                commandGateway.sendAndWait<Any>(cmd)

                return when (quote.signMethod) {
                    SignMethod.SWEDISH_BANK_ID -> swedishBankIdSigningService.startSign(request, memberId, quote.isSwitching)
                    SignMethod.NORWEGIAN_BANK_ID,
                    SignMethod.SIMPLE_SIGN,
                    SignMethod.DANISH_BANK_ID -> throw IllegalArgumentException("Sign method ${quote.signMethod} is not supported in web sign. Use graphql `signQuotes` mutation instead!")
                }
            }
            is QuoteToSignStatusDto.NotEligibleToSign -> throw CannotSignInsuranceException()
        }
    }

    @Transactional
    fun signUnderwriterQuote(memberId: Long, request: UnderwriterQuoteSignRequest): MemberSignUnderwriterQuoteResponse {
        val existing = signedMemberRepository.findBySsn(request.ssn)

        if (existing.isPresent) {
            throw MemberHasExistingInsuranceException()
        }
        return try {
            val signMember = commandGateway.send<Any>(
                SignMemberFromUnderwriterCommand(memberId, request.ssn)
            )
            MemberSignUnderwriterQuoteResponse(memberId, signMember.isDone)
        } catch (exception: Exception) {
            throw CannotSignInsuranceException()
        }
    }

    fun isSsnAlreadySignedMember(ssn: String?): IsSsnAlreadySignedMemberResponse {
        val existing = signedMemberRepository.findBySsn(ssn)
        return IsSsnAlreadySignedMemberResponse(existing.isPresent)
    }

    fun isMemberAlreadySigned(memberId: Long): IsMemberAlreadySignedResponse {
        val existing = signedMemberRepository.findById(memberId)
        return IsMemberAlreadySignedResponse(existing.isPresent)
    }

    fun getSignStatus(@NonNull memberId: Long): SignStatusResponse? {
        val optionalMember = memberRepository.findById(memberId)

        return if (optionalMember.isPresent) {
            val signStrategy =
                underwriterSignSessionRepository.findTopByMemberIdOrderByInternalIdDesc(memberId)?.signStrategy
                    // Fixme: remove this should be out dated in a week when the sessions expires
                    ?: when (underwriterApi.getSignMethodFromQuote(memberId.toString())) {
                        SignMethod.SWEDISH_BANK_ID -> SignStrategy.SWEDISH_BANK_ID
                        SignMethod.NORWEGIAN_BANK_ID,
                        SignMethod.DANISH_BANK_ID -> SignStrategy.REDIRECT_BANK_ID
                        SignMethod.SIMPLE_SIGN -> SignStrategy.SIMPLE_SIGN
                    }

            when (signStrategy) {
                SignStrategy.SWEDISH_BANK_ID -> {
                    val session = swedishBankIdSigningService.getSignSession(memberId)
                    session
                        .map { SignStatusResponse.CreateFromEntity(it) }
                        .orElseGet { null }
                }
                SignStrategy.REDIRECT_BANK_ID -> {
                    zignSecSigningService.getSignStatus(memberId)?.let {
                        SignStatusResponse.CreateFromZignSecStatus(it)
                    }
                }
                SignStrategy.SIMPLE_SIGN -> {
                    simpleSigningService.getSignStatus(memberId)?.let {
                        SignStatusResponse.CreateFromSimpleSignStatus(it)
                    }
                }
            }
        } else {
            null
        }
    }

    @Transactional
    fun scheduleContractsCreatedJob(memberId: Long, signMethod: SignMethod) {
        try {
            val jobName = "POLL_CONTRACTS_CREATE_FOR_${memberId}_JOB"
            val jobDetail = JobBuilder.newJob()
                .withIdentity(jobName, "poll.contracts")
                .setJobData(JobDataMap(mapOf("memberId" to memberId.toString(), "signMethod" to signMethod.name)))
                .ofType(ContractsCreatedCollector::class.java)
                .build()
            val trigger = TriggerBuilder.newTrigger()
                .forJob(jobName, "poll.contracts")
                .withSchedule(
                    SimpleScheduleBuilder
                        .simpleSchedule()
                        .withIntervalInSeconds(1)
                        .withRepeatCount(90)
                        .withMisfireHandlingInstructionNowWithRemainingCount()
                )
                .build()
            scheduler.scheduleJob(
                jobDetail,
                trigger
            )
        } catch (e: SchedulerException) {
            throw RuntimeException(e.message, e)
        }
    }

    @Transactional
    fun completeSwedishSession(id: String?) {
        swedishBankIdSigningService.completeSession(id)
    }

    fun productSignConfirmed(memberId: Long) {
        val member = memberRepository.getOne(memberId)
        val userContext = UpdateUserContextDTO(
            memberId.toString(),
            member.getSsn(),
            member.getFirstName(),
            member.getLastName(),
            member.getPhoneNumber(),
            member.getEmail(),
            member.getStreet(),
            member.getCity(),
            member.zipCode,
            true
        )

        try {
            botService.initBotServiceSessionWebOnBoarding(memberId, userContext)
        } catch (ex: RuntimeException) {
            logger.error("Could not initialize bot-service for memberId: {}", memberId, ex)
        }

        redisEventPublisher.onSignSessionUpdate(memberId)
    }
}
