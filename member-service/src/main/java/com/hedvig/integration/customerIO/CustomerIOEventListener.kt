package com.hedvig.integration.customerIO

import com.hedvig.memberservice.aggregates.MemberStatus
import com.hedvig.memberservice.events.MemberSignedEvent
import com.hedvig.memberservice.query.MemberEntity
import com.hedvig.memberservice.query.MemberRepository
import mu.KotlinLogging
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile

@Profile("customer.io")
@ProcessingGroup("CustomerIO")
class CustomerIOEventListener @Autowired constructor(
    private val memberRepository: MemberRepository,
    private val customerIO: CustomerIO
){
    private val logger = KotlinLogging.logger {}

    @EventHandler
    fun on(event: MemberSignedEvent) {
        val member = memberRepository.findById(event.id).get()
        val membersToDeleteFromCustomerIO = getNonSignedMembersWithSameSsnOrEmail(
            memberId = member.id,
            ssn = member.ssn,
            email = member.email
        )
        membersToDeleteFromCustomerIO.forEach { memberToRemove ->
            try {
                customerIO.deleteUser(userId = memberToRemove.id.toString())
                logger.info { "Deleted member=${memberToRemove.id} from customer.io" }
            } catch (exception: Exception) {
                logger.error { "Failed to delete member=${memberToRemove.id} from customer.io (exception=$exception)" }
            }
        }
    }

    private fun getNonSignedMembersWithSameSsnOrEmail(memberId: Long, ssn: String, email: String): List<MemberEntity> =
        memberRepository.findBySsnOrEmail(ssn, email)
            .filter { member -> member.id != memberId } // To avoid race condition
            .filter { member -> member.status != MemberStatus.SIGNED }
}
