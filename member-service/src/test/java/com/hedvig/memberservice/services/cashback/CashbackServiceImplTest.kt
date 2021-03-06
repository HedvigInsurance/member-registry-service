package com.hedvig.memberservice.services.cashback

import com.hedvig.integration.contentservice.ContentServiceClient
import com.hedvig.integration.contentservice.dto.CashbackOptionDTO
import com.hedvig.memberservice.aggregates.PickedLocale
import com.hedvig.memberservice.commands.SelectNewCashbackCommand
import com.hedvig.memberservice.commands.UpdateAcceptLanguageCommand
import com.hedvig.memberservice.query.MemberEntity
import com.hedvig.memberservice.query.MemberRepository
import com.hedvig.memberservice.services.cashback.CashbackServiceImpl.Companion.DEFAULT_DANISH_CASHBACK_OPTION
import com.hedvig.memberservice.services.cashback.CashbackServiceImpl.Companion.DEFAULT_NORWEGIAN_CASHBACK_OPTION
import com.hedvig.memberservice.services.cashback.CashbackServiceImpl.Companion.DEFAULT_SWEDISH_CASHBACK_OPTION
import com.hedvig.memberservice.services.exceptions.MemberNotFoundException
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.gateway.CommandGateway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.ResponseEntity
import java.util.*

@ExtendWith(MockKExtension::class)
class CashbackServiceImplTest {

    @MockK
    lateinit var contentServiceClient: ContentServiceClient

    @MockK
    lateinit var memberRepository: MemberRepository

    @MockK(relaxed = true)
    lateinit var commandGateway: CommandGateway

    lateinit var classToTest: CashbackServiceImpl

    @BeforeEach
    fun before() {
        classToTest = CashbackServiceImpl(
            contentServiceClient,
            memberRepository,
            commandGateway
        )
    }

    @Test
    fun `when selecting existing cashback option SelectNewCashbackCommand is applied`() {
        val existingCashbackOption = UUID.randomUUID()
        val member = createMember(existingCashbackOption)

        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOptions(member.pickedLocale.name)
        } returns ResponseEntity.ok(listOf(createCashbackOption(existingCashbackOption)))

        classToTest.selectCashbackOption(memberId, existingCashbackOption, null)

        val slot = slot<SelectNewCashbackCommand>()
        verify { commandGateway.sendAndWait(capture(slot)) }
        assertThat(slot.captured.memberId).isEqualTo(memberId)
        assertThat(slot.captured.optionId).isEqualTo(existingCashbackOption)
    }

    @Test
    fun `when selecting non existing cashback option SelectNewCashbackCommand is not sent`() {
        val nonExistingCashbackOption = UUID.randomUUID()
        val member = createMember(nonExistingCashbackOption)

        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOptions(member.pickedLocale.name)
        } returns ResponseEntity.ok(listOf())

        classToTest.selectCashbackOption(memberId, nonExistingCashbackOption, null)

        verify(exactly = 0) {
            commandGateway.sendAndWait(any())
        }
    }

    @Test
    fun `getting cashback option for existing member returns selected cashback option`() {
        val selectedCashbackOptionId = UUID.randomUUID()
        val member = createMember(selectedCashbackOptionId)

        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOption(selectedCashbackOptionId.toString(), member.pickedLocale.name)
        } returns ResponseEntity.ok(createCashbackOption(selectedCashbackOptionId))

        val cashbackOption = classToTest.getMembersCashbackOption(memberId).get()

        assertThat(cashbackOption.id).isEqualTo(selectedCashbackOptionId)
    }

    @Test
    fun `getting cashback option for non existing member throws MemberNotFoundException`() {
        every {
            memberRepository.findById(memberId)
        } returns Optional.empty()

        assertThrows<MemberNotFoundException> {
            classToTest.getMembersCashbackOption(memberId)
        }
    }

    @Test
    fun `getting cashback options for member with picked locale fetches them from content service`() {
        val member = createMember(UUID.randomUUID())
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOptions(member.pickedLocale.name)
        } returns ResponseEntity.ok(listOf())

        classToTest.getOptions(memberId, null)

        verify { contentServiceClient.cashbackOptions(any()) }
    }

    @Test
    fun `getting cashback options for member with no picked locale but accept language fetches them from content service`() {
        val member = createMember(
            cashbackId = UUID.randomUUID(),
            pickedLocale = null,
            acceptLanguage = "sv-SE"
        )
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOptions(any())
        } returns ResponseEntity.ok(listOf())

        classToTest.getOptions(memberId, null)

        verify { contentServiceClient.cashbackOptions(PickedLocale.sv_SE.toString()) }
    }

    @Test
    fun `getting default cashback option id for swedish member returns default swedish cashback option`() {
        val member = createMember(UUID.randomUUID())
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)

        val defaultCashback = classToTest.getDefaultId(memberId)

        assertThat(defaultCashback).isEqualTo(DEFAULT_SWEDISH_CASHBACK_OPTION)
    }

    @Test
    fun `getting default cashback option id for norwegian member returns default norwegian cashback option default danish cashback option`() {
        val member = createMember(UUID.randomUUID(), PickedLocale.en_NO)
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)

        val defaultCashback = classToTest.getDefaultId(memberId)

        assertThat(defaultCashback).isEqualTo(DEFAULT_NORWEGIAN_CASHBACK_OPTION)
    }

    @Test
    fun `getting default cashback option for danish member returns `() {
        val member = createMember(UUID.randomUUID(), PickedLocale.da_DK)
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)

        val defaultCashback = classToTest.getDefaultId(memberId)

        assertThat(defaultCashback).isEqualTo(DEFAULT_DANISH_CASHBACK_OPTION)
    }

    @Test
    fun `getting default cashback option for member fetches from content service`() {
        val member = createMember(UUID.randomUUID(), PickedLocale.da_DK)
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOption(any(), any())
        } returns ResponseEntity.ok(createCashbackOption(UUID.randomUUID()))

        classToTest.getDefaultCashback(memberId)
        verify { contentServiceClient.cashbackOption(any(), any()) }
    }

    @Test
    fun `when selecting existing cashback option on override locale SelectNewCashbackCommand is applied`() {
        val existingCashbackOption = UUID.randomUUID()
        val member = createMember(existingCashbackOption, PickedLocale.sv_SE)
        val overrideLocale = PickedLocale.da_DK

        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOptions(overrideLocale.name)
        } returns ResponseEntity.ok(listOf(createCashbackOption(existingCashbackOption)))

        classToTest.selectCashbackOption(memberId, existingCashbackOption, overrideLocale)

        val slot = slot<SelectNewCashbackCommand>()
        verify { commandGateway.sendAndWait(capture(slot)) }
        assertThat(slot.captured.memberId).isEqualTo(memberId)
        assertThat(slot.captured.optionId).isEqualTo(existingCashbackOption)
    }


    @Test
    fun `getting cashback options for member with override locale fetches them from content service`() {
        val member = createMember(UUID.randomUUID())
        val overrideLocale = PickedLocale.da_DK
        every {
            memberRepository.findById(memberId)
        } returns Optional.of(member)
        every {
            contentServiceClient.cashbackOptions(overrideLocale.name)
        } returns ResponseEntity.ok(listOf())

        classToTest.getOptions(memberId, overrideLocale)

        verify { contentServiceClient.cashbackOptions(overrideLocale.name) }
    }

    private fun createCashbackOption(id: UUID) = CashbackOptionDTO(
        id,
        "",
        "",
        "",
        true,
        "",
        "",
        ""
    )

    private fun createMember(
        cashbackId: UUID,
        pickedLocale: PickedLocale? = PickedLocale.sv_SE,
        acceptLanguage: String? = null
    ) = MemberEntity().apply {
        this.pickedLocale = pickedLocale
        this.cashbackId = cashbackId.toString()
        this.acceptLanguage = acceptLanguage
    }

    companion object {
        private val memberId = 1234L
    }
}
