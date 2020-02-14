package com.hedvig.external.zignSec

import com.hedvig.external.authentication.dto.NorwegianAuthenticationResponse
import com.hedvig.external.zignSec.client.dto.ZignSecNotificationRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hooks/zignsec")
class ZignSecController(
    private val zignSecService: ZignSecService
) {

    @PostMapping(value = ["notifications"])
    fun webhook(@RequestBody request: ZignSecNotificationRequest): ResponseEntity<String> {
        zignSecService.handleNotification(request)
        return ResponseEntity.ok("\uD83D\uDC4D")
    }
}