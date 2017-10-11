package com.hedvig.generic.customerregistry.web;

import com.hedvig.external.billectaAPI.BillectaApi;
import com.hedvig.external.billectaAPI.api.BankIdAuthenticationStatus;
import com.hedvig.external.billectaAPI.api.BankIdStatusType;
import com.hedvig.external.bisnodeBCI.BisnodeClient;
import com.hedvig.external.bisnodeBCI.dto.Person;
import com.hedvig.external.bisnodeBCI.dto.PersonSearchResult;
import com.hedvig.generic.customerregistry.commands.StartOnBoardingCommand;
import com.hedvig.generic.customerregistry.query.MemberEntity;
import com.hedvig.generic.customerregistry.query.MemberRepository;
import com.hedvig.generic.customerregistry.web.dto.BankIdAuthResponse;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController()
@RequestMapping("/member/bankid/")
public class AuthController {

    private final CommandGateway commandBus;
    private final BillectaApi billectaApi;
    private final MemberRepository memberRepo;
    private final BisnodeClient bisnodeClient;
    private static Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    public AuthController(CommandBus commandBus,
                          BillectaApi billectaApi,
                          MemberRepository memberRepo,
                          BisnodeClient bisnodeClient) {
        this.commandBus = new DefaultCommandGateway(commandBus);
        this.billectaApi = billectaApi;
        this.memberRepo = memberRepo;
        this.bisnodeClient = bisnodeClient;
    }

    @RequestMapping(path="auth")
    public ResponseEntity<BankIdAuthResponse> auth(@RequestParam String personalIdentityNumber) {

        Optional<String> ssn = Optional.of(personalIdentityNumber);
        BankIdAuthenticationStatus status = billectaApi.BankIdAuth(ssn);

        BankIdAuthResponse response = null;
        if(status.getStatus() == BankIdStatusType.STARTED) {
            response = new BankIdAuthResponse(status.getStatus(), status.getAutoStartToken(), status.getReferenceToken());
        } else {
            response = new BankIdAuthResponse(status.getStatus(), "", "");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping(path="collect")
    public ResponseEntity<BankIdAuthResponse> collect(@RequestParam String referenceToken, @RequestHeader(value="hedvig.token", required = false) Long hid) {

        BankIdAuthenticationStatus status = billectaApi.BankIdCollect(referenceToken);
        BankIdAuthResponse response = new BankIdAuthResponse(status.getStatus(), status.getAutoStartToken(), status.getReferenceToken());
        if(status.getStatus() == BankIdStatusType.COMPLETE) {
            String ssn = status.getSSN();

            Optional<MemberEntity> member = memberRepo.findBySsn(ssn);

            //If we already have a member with this SSN return his hedvigId.
            if(member.isPresent()) {
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .header("Hedvig.Id", member.get().getId().toString())
                        .body(response);
            }

            member = memberRepo.findById(hid);

            /*
            if(member.get().getSsn() != ssn) {
                log.error("Authenticated SSN does not match ssn from member!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }*/

            List<PersonSearchResult> personList = bisnodeClient.match(ssn).getPersons();
            if(personList.size() != 1) {
                log.error("Could not find person based on personnumer!");
                throw new RuntimeException(("Could not find person at bisnode."));
            }

            Person person = personList.get(0).getPerson();

            StartOnBoardingCommand cmd = new StartOnBoardingCommand(
                    hid,
                    status,
                    person
            );

            commandBus.sendAndWait(cmd);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }

        return ResponseEntity.ok(response);
    }
}