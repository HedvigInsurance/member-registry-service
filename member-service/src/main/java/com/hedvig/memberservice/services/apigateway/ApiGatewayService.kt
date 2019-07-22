package com.hedvig.memberservice.services.apigateway

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ApiGatewayService(
    private val apiGatewayClient: ApiGatewayClient,
    @Value("\${hedvig.memberService.token}") private val token: String
) {
    fun reassignMember(oldMemberId: Long, newMemberId: Long) {
        apiGatewayClient.reassignMember(token, ReassignMemberDto(oldMemberId.toString(), newMemberId.toString()))
    }
}
