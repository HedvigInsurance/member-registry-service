package com.hedvig.memberservice.services.signing.simple

import com.hedvig.memberservice.commands.MemberSimpleSignedCommand
import com.hedvig.memberservice.services.signing.simple.dto.SimpleSignStatus
import com.hedvig.memberservice.services.signing.simple.repository.SimpleSignSession
import com.hedvig.memberservice.services.signing.simple.repository.SimpleSigningSessionRepository
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SimpleSigningServiceImpl(
    private val repository: SimpleSigningSessionRepository,
    private val commandGateway: CommandGateway
) : SimpleSigningService {
    override fun getSignStatus(memberId: Long): SimpleSignStatus? =
        repository.findByMemberId(memberId)?.let { session ->
            if (session.isContractsCreated) {
                SimpleSignStatus.CONTRACTS_CREATED
            } else {
                SimpleSignStatus.INITIATED
            }
        }

    override fun startSign(memberId: Long, ssn: String): UUID {
        val sessionId = UUID.randomUUID()
        repository.save(SimpleSignSession(sessionId, memberId, ssn))
        commandGateway.sendAndWait<Void>(MemberSimpleSignedCommand(memberId, ssn, sessionId))
        return sessionId
    }

    override fun notifyContractsCreated(memberId: Long) {
        val session = repository.findByMemberId(memberId)
            ?: throw RuntimeException("Was notified by contract created but no simple sign session was found for memberId: $memberId")

        session.isContractsCreated = true
        repository.save(session)
    }
}
