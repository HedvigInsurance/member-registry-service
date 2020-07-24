package com.hedvig.external.zignSec.repository

import com.hedvig.external.zignSec.repository.entitys.ZignSecSignEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ZignSecSignEntityRepository: CrudRepository<ZignSecSignEntity, Long> {
    fun findByIdProviderPersonId(idProviderPersonId: String): ZignSecSignEntity?
    fun findByPersonalNumber(personalNumber: String): ZignSecSignEntity?
}
