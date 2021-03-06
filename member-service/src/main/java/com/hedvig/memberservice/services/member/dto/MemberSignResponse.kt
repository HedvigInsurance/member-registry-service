package com.hedvig.memberservice.services.member.dto

import com.hedvig.external.bankID.bankIdTypes.OrderResponse
import com.hedvig.memberservice.entities.SignStatus

data class MemberSignResponse(
    val signId: Long? = null,
    val status: SignStatus,
    val bankIdOrderResponse: OrderResponse? = null
)
