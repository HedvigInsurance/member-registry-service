package com.hedvig.memberservice.aggregates;

import com.hedvig.external.bisnodeBCI.BisnodeClient;
import com.hedvig.external.bisnodeBCI.dto.Person;
import com.hedvig.external.bisnodeBCI.dto.PersonSearchResult;
import com.hedvig.memberservice.commands.*;
import com.hedvig.memberservice.events.*;
import com.hedvig.memberservice.services.CashbackService;
import com.hedvig.memberservice.web.dto.CashbackOption;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.ApplyMore;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * This is an example Aggregate and should be remodeled to suit the needs of you domain.
 */
@Aggregate
public class MemberAggregate {

    @AggregateIdentifier
    public Long id;

    private Logger log = LoggerFactory.getLogger(MemberAggregate.class);

    private  BisnodeClient bisnodeClient;

    private MemberStatus status;

    private Member member;

    private BisnodeInformation latestBisnodeInformation;

    private CashbackService cashbackService;

    @Autowired
    public MemberAggregate(BisnodeClient bisnodeClient, CashbackService cashbackService) {
        this.bisnodeClient = bisnodeClient;
        this.cashbackService = cashbackService;
    }

    @CommandHandler
    public MemberAggregate(CreateMemberCommand command) {
        apply(new MemberCreatedEvent(command.getMemberId(), MemberStatus.INITIATED));
    }

    @CommandHandler
    void authAttempt(AuthenticationAttemptCommand command) {

        ApplyMore applyChain = null;

        if(this.status == MemberStatus.INITIATED) {
            //Trigger fetching of bisnode data.
            String ssn = command.getBankIdAuthResponse().getSSN();
            List<PersonSearchResult> personList = bisnodeClient.match(ssn).getPersons();
            if (personList.size() != 1) {
                log.error("Could not find person based on personnumer!");
                throw new RuntimeException("Could not find person at bisnode.");
            }

            Person person = personList.get(0).getPerson();

            applyChain = apply(new NameUpdatedEvent(this.id, person.getPreferredFirstName(), person.getFamilyName()));
            applyChain = applyChain.andThenApply(() -> new SSNUpdatedEvent(this.id, ssn));

            BisnodeInformation pi = new BisnodeInformation(ssn, person);
            if(pi.getAddress().isPresent()) {
                 applyChain = applyChain.andThenApply(() -> new LivingAddressUpdatedEvent(this.id, pi.getAddress().get()));
            }

            applyChain = applyChain.
                    andThenApply(() -> new PersonInformationFromBisnodeEvent(this.id, pi)).
                    andThenApply(() -> new MemberStartedOnBoardingEvent(this.id, MemberStatus.ONBOARDING));
        }

        MemberAuthenticatedEvent authenticatedEvent = new MemberAuthenticatedEvent(this.id, command.getBankIdAuthResponse().getReferenceToken());

        if(applyChain != null)
        {
            applyChain.andThenApply( () ->  authenticatedEvent);
        }
        else {
            apply(authenticatedEvent);
        }

    }

    @CommandHandler
    void inactivateMember(InactivateMemberCommand command) {
        if(this.status == MemberStatus.INITIATED) {
            apply(new MemberInactivatedEvent(this.id));
        } else {
            String str = String.format("Cannot INACTIAVTE member %s in status: %s", this.id, this.status.name());
            throw new RuntimeException(str);
        }
    }

    @CommandHandler
    void startOnboardingWithSSNCommand(StartOnboardingWithSSNCommand comand) {
        apply(new OnboardingStartedWithSSNEvent(this.id, comand.getSsn()));
        apply(new MemberStartedOnBoardingEvent(this.id, MemberStatus.ONBOARDING));
    }

    @CommandHandler
    void updatePersonalInformation(MemberUpdateContactInformationCommand cmd) {

        if(!Objects.equals(this.member.getFirstName(),cmd.getFirstName()) ||
                !Objects.equals(this.member.getLastName(), cmd.getLastName())) {
            apply(new NameUpdatedEvent(this.id, cmd.getFirstName(), cmd.getLastName()));
        }

        LivingAddress address = this.member.getLivingAddress();
        if(address == null || address.needsUpdate(cmd.getStreet(), cmd.getCity(), cmd.getZipCode(), cmd.getApartmentNo())) {
            apply(new LivingAddressUpdatedEvent(this.id, cmd.getStreet(), cmd.getCity(), cmd.getZipCode(), cmd.getApartmentNo()));
        }
    }

    @CommandHandler
    void bankIdSignHandler(BankIdSignCommand cmd) {
        apply(new NewCashbackSelectedEvent(this.id, cashbackService.getDefaultId().toString()));
        apply(new MemberSignedEvent(this.id, cmd.getReferenceId()));
    }

    @CommandHandler
    void selectNewCashback(SelectNewCashbackCommand cmd) {
        apply(new NewCashbackSelectedEvent(this.id, cmd.getOptionId().toString()));
    }

    /*
    @CommandHandler
    void finalizeOnBoarding(MemberUpdateContactInformationCommand cmd) {
        apply(new EmailUpdatedEvent(this.id, cmd.getEmail()));
        apply(new LivingAddressUpdatedEvent(this.id, cmd.getStreet(), cmd.getCity(), cmd.getZipCode(), cmd.getApartmentNo()));
        apply(new NameUpdatedEvent(this.id, cmd.getFirstName(), cmd.getLastName()));
        apply(new SSNUpdatedEvent(this.id, cmd.getSsn()));
    }*/

    @EventSourcingHandler
    public void on(MemberCreatedEvent e) {
        this.id = e.getId();
        this.status = e.getStatus();
        this.member = new Member();
    }

    @EventSourcingHandler
    public void on(MemberStartedOnBoardingEvent e){
        this.status = e.getNewStatus();
    }

    @EventSourcingHandler
    public void on(PersonInformationFromBisnodeEvent e) {
        this.latestBisnodeInformation = e.getInformation();
    }

    @EventSourcingHandler
    public void on(EmailUpdatedEvent e) {
        this.member.setEmail(e.getEmail());
    }

    @EventSourcingHandler
    public void on(LivingAddressUpdatedEvent e) {

        LivingAddress address = new LivingAddress(e.getStreet(), e.getCity(), e.getZipCode(), e.getApartmentNo());
        this.member.setLivingAddress(address);
    }

    @EventSourcingHandler
    public  void on(OnboardingStartedWithSSNEvent e) {
        this.member.setSsn(e.getSsn());
    }

}

