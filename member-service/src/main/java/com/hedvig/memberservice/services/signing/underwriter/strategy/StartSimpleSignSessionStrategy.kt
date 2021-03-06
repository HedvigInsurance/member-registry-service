package com.hedvig.memberservice.services.signing.underwriter.strategy

import com.hedvig.memberservice.services.signing.simple.SimpleSigningService
import com.hedvig.memberservice.web.dto.UnderwriterStartSignSessionRequest
import com.hedvig.memberservice.web.dto.UnderwriterStartSignSessionResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StartSimpleSignSessionStrategy(
    private val simpleSigningService: SimpleSigningService,
    private val commonSessionCompletion: CommonSessionCompletion
) : StartSignSessionStrategy<UnderwriterStartSignSessionRequest.SimpleSign, UnderwriterStartSignSessionResponse.SimpleSign, UnderwriterSessionCompletedData.SimpleSign> {

    override val signStrategy = SignStrategy.SIMPLE_SIGN

    override fun startSignSession(memberId: Long, request: UnderwriterStartSignSessionRequest.SimpleSign, createUnderwriterSignSession: (UUID, SignStrategy) -> Unit): UnderwriterStartSignSessionResponse.SimpleSign {
        val signSessionId = UUID.randomUUID()

        createUnderwriterSignSession.invoke(signSessionId, signStrategy)
        simpleSigningService.startSign(signSessionId, memberId, request.nationalIdentification)

        return UnderwriterStartSignSessionResponse.SimpleSign(true)
    }

    override fun signSessionWasCompleted(signSessionReference: UUID, data: UnderwriterSessionCompletedData.SimpleSign) {
        commonSessionCompletion.signSessionWasCompleted(signSessionReference)
    }
}
