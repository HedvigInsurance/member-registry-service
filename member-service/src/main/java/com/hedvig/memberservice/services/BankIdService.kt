package com.hedvig.memberservice.services

import com.hedvig.memberservice.query.CollectType.RequestType

import com.hedvig.external.bankID.bankidTypes.CollectResponse
import com.hedvig.external.bankID.bankidTypes.OrderResponse
import com.hedvig.memberservice.query.CollectRepository
import com.hedvig.memberservice.query.CollectType
import com.hedvig.memberservice.services.bankid.BankIdSOAPApi
import java.io.UnsupportedEncodingException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BankIdService(
    private val bankIdSOAPApi: BankIdSOAPApi,
    private val collectRepository: CollectRepository
) {
    private val log = LoggerFactory.getLogger(BankIdService::class.java)

    fun auth(memberId: Long?): OrderResponse {
        val status = bankIdSOAPApi.auth()
        log.info(
            "Started bankId AUTH autostart:{}, reference:{}",
            status.autoStartToken,
            status.orderRef)
        trackAuthToken(status.orderRef, memberId)
        return status
    }

    @Throws(UnsupportedEncodingException::class)
    fun sign(ssn: String, userMessage: String, memberId: Long?): OrderResponse {
        val status = bankIdSOAPApi.sign(ssn, userMessage)
        trackSignToken(status.orderRef, memberId)
        return status
    }

    private fun trackSignToken(referenceToken: String, memberId: Long?) {
        trackReferenceToken(referenceToken, RequestType.SIGN, memberId)
    }

    private fun trackAuthToken(referenceToken: String, memberId: Long?) {
        trackReferenceToken(referenceToken, RequestType.AUTH, memberId)
    }

    private fun trackReferenceToken(referenceToken: String, sign: RequestType, memberId: Long?) {
        val ct = CollectType()
        ct.token = referenceToken
        ct.type = sign
        ct.memberId = memberId
        collectRepository.save(ct)
    }

    fun authCollect(referenceToken: String): CollectResponse {
        return bankIdSOAPApi.authCollect(referenceToken)
    }

    fun signCollect(referenceToken: String): CollectResponse {
        return bankIdSOAPApi.signCollect(referenceToken)
    }

}