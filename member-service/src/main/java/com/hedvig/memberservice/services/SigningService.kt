package com.hedvig.memberservice.services

import com.hedvig.integration.botService.BotService
import com.hedvig.integration.botService.dto.UpdateUserContextDTO
import com.hedvig.integration.underwriter.UnderwriterApi
import com.hedvig.integration.underwriter.dtos.QuoteToSignStatusDto
import com.hedvig.integration.underwriter.dtos.SignMethod
import com.hedvig.memberservice.commands.SignMemberFromUnderwriterCommand
import com.hedvig.memberservice.commands.UpdateWebOnBoardingInfoCommand
import com.hedvig.memberservice.query.MemberRepository
import com.hedvig.memberservice.query.SignedMemberRepository
import com.hedvig.memberservice.services.member.CannotSignInsuranceException
import com.hedvig.memberservice.services.member.dto.MemberSignResponse
import com.hedvig.memberservice.services.member.dto.MemberSignUnderwriterQuoteResponse
import com.hedvig.memberservice.web.dto.IsSsnAlreadySignedMemberResponse
import com.hedvig.memberservice.web.v2.dto.SignStatusResponse
import com.hedvig.memberservice.web.v2.dto.UnderwriterQuoteSignRequest
import com.hedvig.memberservice.web.v2.dto.WebsignRequest
import org.axonframework.commandhandling.gateway.CommandGateway
import org.slf4j.LoggerFactory
import org.springframework.lang.NonNull
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class SigningService(
    private val underwriterApi: UnderwriterApi,
    private val signedMemberRepository: SignedMemberRepository,
    private val botService: BotService,
    private val memberRepository: MemberRepository,
    private val commandGateway: CommandGateway,
    private val swedishBankIdSigningService: SwedishBankIdSigningService,
    private val norwegianSigningService: NorwegianSigningService
) {

    @Transactional
    fun startWebSign(memberId: Long, request: WebsignRequest): MemberSignResponse {

        val existing = signedMemberRepository.findBySsn(request.ssn)

        if (existing.isPresent) {
            throw MemberHasExistingInsuranceException()
        }

        when (val quote = underwriterApi.hasQuoteToSign(memberId.toString())) {
            is QuoteToSignStatusDto.EligibleToSign -> {
                val cmd = UpdateWebOnBoardingInfoCommand(memberId, request.ssn, request.email)
                commandGateway.sendAndWait<Any>(cmd)

                return when (quote.signMethod) {
                    SignMethod.SWEDISH_BANK_ID -> swedishBankIdSigningService.startSign(request, memberId, quote.isSwitching)
                    SignMethod.NORWEGIAN_BANK_ID-> norwegianSigningService.startSign(memberId, request)
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
                SignMemberFromUnderwriterCommand(memberId, request.ssn))
            MemberSignUnderwriterQuoteResponse(memberId, signMember.isDone)
        } catch (exception: Exception) {
            throw CannotSignInsuranceException()
        }
    }

    fun IsSsnAlreadySignedMember(ssn: String?): IsSsnAlreadySignedMemberResponse {
        val existing = signedMemberRepository.findBySsn(ssn)
        return IsSsnAlreadySignedMemberResponse(existing.isPresent)
    }

    fun getSignStatus(@NonNull memberId: Long): SignStatusResponse? {
        val optionalMember = memberRepository.findById(memberId)

        return if (optionalMember.isPresent) {
            when (val quote = underwriterApi.hasQuoteToSign(memberId.toString())) {
                is QuoteToSignStatusDto.EligibleToSign -> {
                    when(quote.signMethod) {
                        SignMethod.SWEDISH_BANK_ID  -> {
                            val session = swedishBankIdSigningService.getSignSession(memberId)
                            session
                                .map { SignStatusResponse.CreateFromEntity(it) }
                                .orElseGet { null }
                        }
                        SignMethod.NORWEGIAN_BANK_ID -> {
                            norwegianSigningService.getSignStatus(memberId)?.let {
                                SignStatusResponse.CreateFromNorwegianStatus(it)
                            }
                        }
                    }
                }
                is QuoteToSignStatusDto.NotEligibleToSign -> null
            }
        } else {
            null
        }
    }

    @Transactional
    fun completeSwedishSession(id: String?) {
        swedishBankIdSigningService.completeSession(id)
    }

    fun productSignConfirmed(memberId: Long){
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
            true)

        try {
            botService.initBotServiceSessionWebOnBoarding(memberId, userContext)
        } catch (ex: RuntimeException) {
            log.error("Could not initialize bot-service for memberId: {}", memberId, ex)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SigningService::class.java)
    }
}