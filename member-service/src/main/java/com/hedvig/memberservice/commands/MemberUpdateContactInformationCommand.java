package com.hedvig.memberservice.commands;

import com.hedvig.memberservice.web.dto.UpdateContactInformationRequest;
import lombok.Value;
import org.axonframework.commandhandling.TargetAggregateIdentifier;

@Value
public class MemberUpdateContactInformationCommand {

    @TargetAggregateIdentifier
    long memberId;

    String firstName;
    String lastName;
    //String ssn;
    //String email;

    String street;
    String city;
    String zipCode;
    String apartmentNo;


    public MemberUpdateContactInformationCommand(Long memberId, UpdateContactInformationRequest body) {
        this.memberId = memberId;

        this.firstName = body.getFirstName();
        this.lastName = body.getLastName();
        //this.ssn = body.getSsn();
        //this.email = body.getEmail();

        this.street = body.getAddress().getStreet();
        this.city = body.getAddress().getCity();
        this.zipCode = body.getAddress().getZipCode();
        this.apartmentNo = body.getAddress().getApartmentNo();

    }
}