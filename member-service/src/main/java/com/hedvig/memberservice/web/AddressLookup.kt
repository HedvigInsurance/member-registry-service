package com.hedvig.memberservice.web

import com.hedvig.external.bisnodeBCI.BisnodeClient
import com.hedvig.memberservice.web.dto.Address
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Digits
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size

data class SweAddressRequest(
    @NotEmpty
    @Digits(integer = 12, fraction = 0)
    @Size(min = 12, max = 12)
    val ssn: String,
    @NotEmpty
    val memberId: String
)

data class SweAddressResponse(
    val firstName: String,
    val lastName: String,
    val address: Address?
)

@RestController
@RequestMapping("/_/addresslookup")
class AddressLookup(private val client: BisnodeClient) {


    @PostMapping("swe")
    fun getAddressFromSwePersonnummer(
        @RequestBody request: SweAddressRequest
    ): ResponseEntity<Any> {

        val match = client.match(request.ssn)
        if (match.persons.size == 1) {
            val person = match.persons[0].person
            val address =  person.addressList.getOrNull(0)
            val addr = if (address?.streetName != null) {

                Address("${address.streetName} ${address.streetNumber.orEmpty()}${address.entrance.orEmpty()}", address.city, address.postalCode, address.apartment)
            } else null

            return ResponseEntity.ok(SweAddressResponse(person.preferredOrFirstName, person.familyName, addr))
        }

        return ResponseEntity.notFound().build()
    }
}