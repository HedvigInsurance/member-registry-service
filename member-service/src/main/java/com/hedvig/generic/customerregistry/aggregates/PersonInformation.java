package com.hedvig.generic.customerregistry.aggregates;



import com.hedvig.external.bisnodeBCI.dto.Person;
import lombok.Getter;
import lombok.Setter;

import javax.tools.JavaCompiler;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Getter
@Setter
public class PersonInformation {
    private String ssn;
    private String bisnodeGEDI;
    private String preferredFirstName;
    private Boolean privateIdentity;
    private List<String> firstNames;
    private String familyName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private Boolean deceased;
    private String dateOfDeath;
    private Boolean directMarketingRestriction;

    private List<Address> addressList;

    private List<Telephone> phoneList;

    public PersonInformation(){};

    public PersonInformation(Person bisnodePerson) {
        //this.ssn = bisnodePerson.getLegalId();
        this.bisnodeGEDI = bisnodePerson.getGedi();
        this.preferredFirstName = bisnodePerson.getPreferredFirstName();
        this.privateIdentity = bisnodePerson.getPrivateIdentity();
        this.firstNames = bisnodePerson.getFirstNames();
        this.familyName = bisnodePerson.getFamilyName();

        if(bisnodePerson.getGender() != null)
            this.gender = Gender.valueOf(bisnodePerson.getGender().name());

        this.dateOfBirth = bisnodePerson.getDateOfBirth();
        this.deceased = bisnodePerson.getDeceased();
        this.dateOfDeath = bisnodePerson.getDateOfDeath();
        this.directMarketingRestriction = bisnodePerson.getDirectMarketingRestriction();

        addressList = bisnodePerson.getAddressList().stream().map(a -> new com.hedvig.generic.customerregistry.aggregates.Address(
                a.getType(),
                a.getCareOf(),
                a.getStreetName(),
                a.getStreetNumber(),
                a.getEntrance(),
                a.getApartment(),
                a.getFloor(),
                a.getPostOfficeBox(),
                a.getPostalCode(),
                a.getCity(),
                a.getCountry(),
                a.getFormattedAddress()
                )).collect(Collectors.toList());

        phoneList = bisnodePerson.getPhoneList().stream().map( p -> new Telephone(
                p.getType(),
                p.getNumber(),
                p.getTelemarketingRestriction(),
                p.getSecretPhoneNumber())).collect(Collectors.toList());
    }

    public String getFullName() {

        return String.format("%s %s",
                Optional.ofNullable(preferredFirstName).orElse(""),
                Optional.of(familyName).orElse(""));
    }

    public Optional<Address> getAddress(){
        if(addressList.size() < 1) {
            return Optional.empty();
        }

        return Optional.of(addressList.get(0));
    }
}