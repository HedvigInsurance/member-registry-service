package com.hedvig.personservice.persons.domain.sagas

import com.hedvig.external.syna.SynaService
import com.hedvig.personservice.debts.model.DebtSnapshot
import com.hedvig.personservice.persons.domain.commands.SynaDebtCheckedCommand
import com.hedvig.personservice.persons.domain.events.CheckPersonDebtEvent
import com.hedvig.personservice.persons.query.PersonRepository
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.saga.EndSaga
import org.axonframework.eventhandling.saga.SagaEventHandler
import org.axonframework.eventhandling.saga.StartSaga
import org.axonframework.spring.stereotype.Saga
import org.springframework.beans.factory.annotation.Autowired

@Saga
class CheckPersonDebtSaga {
    @Transient
    @Autowired
    lateinit var synaService: SynaService

    @Transient
    @Autowired
    lateinit var personRepository: PersonRepository

    @Transient
    @Autowired
    lateinit var commandGateway: CommandGateway

    @StartSaga
    @SagaEventHandler(associationProperty = "ssn")
    @EndSaga
    fun on(event: CheckPersonDebtEvent) {
        val synaDebtCheck = synaService.getDebtCheck(event.ssn)
        val person = personRepository.findBySsn(event.ssn)!!
        commandGateway.sendAndWait<Void>(SynaDebtCheckedCommand(event.ssn, DebtSnapshot.from(
            person = person,
            synaDebtCheck = synaDebtCheck
        )))
    }
}
