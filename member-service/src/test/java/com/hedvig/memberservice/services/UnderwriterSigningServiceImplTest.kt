package com.hedvig.memberservice.services

import com.hedvig.external.bankID.bankIdTypes.OrderResponse
import com.hedvig.memberservice.entities.UnderwriterSignSessionEntity
import com.hedvig.memberservice.query.UnderwriterSignSessionRepository
import com.hedvig.memberservice.services.member.dto.StartSwedishSignResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.*
import org.mockito.Mockito.`when` as whenever

@RunWith(MockitoJUnitRunner::class)
class UnderwriterSigningServiceImplTest {

    @Mock
    lateinit var underwriterSignSessionRepository: UnderwriterSignSessionRepository

    @Mock
    lateinit var swedishBankIdSigningService: SwedishBankIdSigningService

    @Captor
    lateinit var captor: ArgumentCaptor<UnderwriterSignSessionEntity>

    private lateinit var sut: UnderwriterSigningService

    @Before
    fun setup() {
        sut = UnderwriterSigningServiceImpl()
    }

    @Test
    fun startSwedishBankIdSignSession() {
        whenever(swedishBankIdSigningService.startSign(memberId, swedishSSN, ip, false))
            .thenReturn(StartSwedishSignResponse(
                signId = 0,
                bankIdOrderResponse = OrderResponse(
                    swedishOrderRef,
                    autoStartToken
                )
            ))

        whenever(underwriterSignSessionRepository.save(captor.capture()))

        val response = sut.startSwedishBankIdSignSession(memberId, swedishSSN, ip, false)

        assertThat(response.autoStartToken).isEqualTo(autoStartToken)
        assertThat(captor.value.signReference).isEqualTo(swedishOrderRef)
    }

    companion object {
        private val underwriterSessionRef = UUID.randomUUID()
        private const val swedishOrderRef = "orderRef"
        private const val autoStartToken = "autoStartToken"
        private const val memberId = 1337L
        private const val swedishSSN = "1912121212"
        private const val ip = "1.0.0.0"
    }
}
