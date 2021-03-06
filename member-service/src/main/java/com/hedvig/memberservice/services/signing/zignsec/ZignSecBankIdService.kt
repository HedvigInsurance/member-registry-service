package com.hedvig.memberservice.services.signing.zignsec

import com.hedvig.external.authentication.ZignSecAuthentication
import com.hedvig.external.authentication.dto.StartZignSecAuthenticationResult
import com.hedvig.external.authentication.dto.ZignSecAuthenticationResult
import com.hedvig.external.authentication.dto.ZignSecBankIdAuthenticationRequest
import com.hedvig.integration.apigateway.ApiGatewayService
import com.hedvig.memberservice.commands.InactivateMemberCommand
import com.hedvig.memberservice.commands.ZignSecSuccessfulAuthenticationCommand
import com.hedvig.memberservice.commands.models.ZignSecAuthenticationMarket
import com.hedvig.memberservice.query.MemberRepository
import com.hedvig.memberservice.query.SignedMemberRepository
import com.hedvig.memberservice.services.redispublisher.AuthSessionUpdatedEventStatus
import com.hedvig.memberservice.services.redispublisher.RedisEventPublisher
import com.hedvig.memberservice.util.logger
import com.hedvig.resolver.LocaleResolver
import org.axonframework.commandhandling.gateway.CommandGateway
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.util.*

@Service
class ZignSecBankIdService(
    private val zignSecAuthentication: ZignSecAuthentication,
    private val commandGateway: CommandGateway,
    private val redisEventPublisher: RedisEventPublisher,
    private val signedMemberRepository: SignedMemberRepository,
    private val apiGatewayService: ApiGatewayService,
    private val memberRepository: MemberRepository,
    @Value("\${redirect.authentication.successUrl}")
    private val authenticationSuccessUrl: String,
    @Value("\${redirect.authentication.failUrl}")
    private val authenticationFailUrl: String,
    @Value("\${static.authentication.redirect.baseUrl}")
    private val baseUrl: String
) {

    companion object {
        const val NORWEGIAN_BANK_ID_NORWEGIAN_LOGIN_PATH = "no/login"
        const val NORWEGIAN_BANK_ID_ENGLISH_LOGIN_PATH = "no-en/login"
    }

    fun authenticate(
        memberId: Long,
        personalNumber: String?,
        market: ZignSecAuthenticationMarket,
        acceptLanguage: String?,
        token: String
    ): StartZignSecAuthenticationResult {
        if (market == ZignSecAuthenticationMarket.NORWAY && personalNumber == null) {
            return StartZignSecAuthenticationResult.StaticRedirect(
                resolveNorwegianLoginUrl(memberId, acceptLanguage, token)
            )
        }

        return zignSecAuthentication.auth(
            ZignSecBankIdAuthenticationRequest(
                memberId.toString(),
                personalNumber,
                resolveTwoLetterLanguageFromMember(memberId, acceptLanguage),
                authenticationSuccessUrl,
                authenticationFailUrl,
                market.getAuthenticationMethod()
            )
        )
    }

    fun sign(
        memberId: String,
        ssn: String,
        successUrl: String,
        failUrl: String,
        zignSecAuthenticationMarket: ZignSecAuthenticationMarket
    ) = zignSecAuthentication.sign(
        ZignSecBankIdAuthenticationRequest(
            memberId,
            ssn,
            resolveTwoLetterLanguageFromMember(memberId.toLong(), null),
            successUrl,
            failUrl,
            zignSecAuthenticationMarket.getAuthenticationMethod()
        )
    )

    fun completeAuthentication(result: ZignSecAuthenticationResult) {
        when (result) {
            is ZignSecAuthenticationResult.Completed -> {
                MDC.put("zignSecSign.memberId", result.memberId.toString())

                signedMemberRepository.findBySsn(result.ssn).ifPresentOrElse(
                    { signedMember ->
                        MDC.put("signedMember.memberId", signedMember.id.toString())
                        logger.info("ZignSec auth completion: Found existing signed member by SSN match")

                        if (result.memberId != signedMember.id) {
                            logger.info("ZignSec auth completion: MemberID mismatch, inactivating ${result.memberId}")
                            commandGateway.sendAndWait<Void>(InactivateMemberCommand(result.memberId))
                            apiGatewayService.reassignMember(result.memberId, signedMember.id)
                        }
                        logger.info("ZignSec auth completion: Sending success command")
                        commandGateway.sendAndWait<Void>(
                            ZignSecSuccessfulAuthenticationCommand(
                                signedMember.id,
                                result.id,
                                result.ssn,
                                ZignSecAuthenticationMarket.fromAuthenticationMethod(result.authenticationMethod),
                                result.firstName,
                                result.lastName
                            )
                        )
                        logger.info("ZignSec auth completion: Publishing session to redis")
                        redisEventPublisher.onAuthSessionUpdated(result.memberId, AuthSessionUpdatedEventStatus.SUCCESS)
                    },
                    {
                        redisEventPublisher.onAuthSessionUpdated(result.memberId, AuthSessionUpdatedEventStatus.FAILED)
                    }
                )
            }
            is ZignSecAuthenticationResult.Failed ->
                redisEventPublisher.onAuthSessionUpdated(result.memberId, AuthSessionUpdatedEventStatus.FAILED)
        }
    }

    fun getStatus(memberId: Long) = zignSecAuthentication.getStatus(memberId)

    fun notifyContractsCreated(memberId: Long) = zignSecAuthentication.notifyContractsCreated(memberId)

    private fun resolveNorwegianLoginUrl(
        memberId: Long,
        acceptLanguage: String?,
        token: String
    ): String {
        val path = when (resolveLocaleFromMember(memberId, acceptLanguage)?.language) {
            Locale("nb").language -> NORWEGIAN_BANK_ID_NORWEGIAN_LOGIN_PATH
            else -> NORWEGIAN_BANK_ID_ENGLISH_LOGIN_PATH
        }
        return "$baseUrl$path#token=${URLEncoder.encode(token, Charsets.UTF_8)}"
    }

    private fun resolveTwoLetterLanguageFromMember(memberId: Long, acceptLanguage: String?) = when (resolveLocaleFromMember(memberId, acceptLanguage)?.language) {
        Locale("sv").language -> "SV"
        Locale("en").language -> "EN"
        Locale("nb").language -> "NO"
        Locale("da").language -> "DA"
        else -> "EN"
    }

    private fun resolveLocaleFromMember(memberId: Long, acceptLanguage: String?): Locale? {
        return memberRepository.findByIdOrNull(memberId)?.let {
            it.pickedLocale?.locale
                ?: LocaleResolver.resolveNullableLocale(acceptLanguage)
                ?: LocaleResolver.resolveNullableLocale(it.acceptLanguage)
        } ?: LocaleResolver.resolveNullableLocale(acceptLanguage)
    }
}

