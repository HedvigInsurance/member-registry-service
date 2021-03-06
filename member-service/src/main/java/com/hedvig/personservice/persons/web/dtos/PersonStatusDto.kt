package com.hedvig.personservice.persons.web.dtos

import com.hedvig.personservice.persons.model.Flag
import com.hedvig.personservice.persons.model.Person

data class PersonStatusDto(
    val flag: Flag,
    val isWhitelisted: Boolean
) {
    companion object {
        fun from(person: Person, personFlag: Flag) = PersonStatusDto(
            flag = personFlag,
            isWhitelisted = person.whitelisted != null
        )
    }
}
