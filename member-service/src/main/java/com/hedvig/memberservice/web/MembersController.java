package com.hedvig.memberservice.web;

import com.google.common.collect.Lists;
import com.hedvig.memberservice.commands.*;
import com.hedvig.integration.productsPricing.ProductApi;
import com.hedvig.integration.productsPricing.dto.InsuranceStatusDTO;
import com.hedvig.memberservice.query.MemberEntity;
import com.hedvig.memberservice.query.MemberRepository;
import com.hedvig.memberservice.query.TrackingIdEntity;
import com.hedvig.memberservice.query.TrackingIdRepository;
import com.hedvig.memberservice.services.CashbackService;
import com.hedvig.memberservice.web.dto.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@RestController()
@RequestMapping("/member/")
public class MembersController {

    private final MemberRepository repo;
    private final TrackingIdRepository trackingRepo;
    private final CommandGateway commandGateway;
    private final ProductApi productApi;
    private final CashbackService cashbackService;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${hedvig.counterkey:123}")
    public String counterKey;

    @Autowired
    public MembersController(MemberRepository repo,
                             CommandGateway commandGateway,
                             ProductApi productApi,
                             CashbackService cashbackService,
                             TrackingIdRepository trackingRepo) {
        this.repo = repo;
        this.commandGateway = commandGateway;
        this.productApi = productApi;
        this.cashbackService = cashbackService;
        this.trackingRepo = trackingRepo;
    }

    @RequestMapping(path = "/counter/321432", method = RequestMethod.GET)
    public ResponseEntity<CounterDTO> getCount(@RequestParam String key) {

        CounterDTO count = new CounterDTO();
        count.number = 123l;
        if (key.equals(counterKey)) {
            count.number = repo.countSignedMembers() + 100000l; // Hack to solve the broken Smiirl counter
        }
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<Member> index(@PathVariable Long memberId) {

        Optional<MemberEntity> member = repo.findById(memberId);
        if (member.isPresent()) {

            return ResponseEntity.ok(new Member(member.get()));
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "hedvig.token") Long hid) {
        Optional<MemberEntity> m = repo.findById(hid);

        MemberEntity me = m.orElseGet(() -> {
            MemberEntity m2 = new MemberEntity();
            m2.setFirstName("");
            m2.setLastName("");
            m2.setBirthDate(LocalDate.now());
            m2.setStreet("");
            m2.setCity("");
            m2.setApartment("");
            m2.setStatus(null);
            m2.setSsn("");
            m2.setEmail("");
            m2.setCashbackId(cashbackService.getDefaultId().toString());
            return m2;
        });

        CashbackOption cashbackOption = null;

        if (me.getCashbackId() != null) {
            cashbackOption = cashbackService.getCashbackOption(UUID.fromString(me.getCashbackId()))
                    .orElseGet(cashbackService::getDefaultCashback);
        }

        InsuranceStatusDTO insuranceStatus = this.productApi.getInsuranceStatus(hid);
        Optional<TrackingIdEntity> tId = trackingRepo.findByMemberId(hid);

        MemberMeDTO p = new MemberMeDTO(
                me.getId().toString(),
                me.getSsn(),
                String.format("%s %s", me.getFirstName(), me.getLastName()), me.getFirstName(),
                me.getLastName(),
                new ArrayList<>(),
                me.getBirthDate() == null ? null : me.getBirthDate().until(LocalDate.now()).getYears(),
                me.getEmail(),
                me.getStreet(),
                0,
                insuranceStatus.getInsuranceStatus().equals("ACTIVE") ? "Betalas via autogiro"
                        : "Betalning sätts upp när försäkringen aktiveras", // ""XXXX XXXX 1234",
                cashbackOption == null ? null : cashbackOption.name, insuranceStatus.getInsuranceStatus(),
                insuranceStatus.getInsuranceStatus().equals("ACTIVE") ? LocalDate.now().withDayOfMonth(25)
                        : null,
                cashbackOption == null ? null : cashbackOption.signature,
                cashbackOption == null ? null : String.format(cashbackOption.paragraph, me.getFirstName()),
                cashbackOption == null ? null : cashbackOption.selectedUrl,
                insuranceStatus.getSafetyIncreasers().size() == 1 && insuranceStatus.getSafetyIncreasers().get(0).isEmpty() ? Lists.newArrayList() : insuranceStatus.getSafetyIncreasers(),
                tId.map(TrackingIdEntity::getTrackingId).orElse(null),
                me.getPhoneNumber());

        return ResponseEntity.ok(p);
    }

    @PostMapping("/email")
    public ResponseEntity<?> postEmail(@RequestHeader(value = "hedvig.token") Long hid, @RequestBody @Valid PostEmailRequestDTO body) {

        commandGateway.sendAndWait(new UpdateEmailCommand(hid, body.getEmail()));

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/phonenumber")
    public ResponseEntity<?> postPhoneNumber(@RequestHeader(value = "hedvig.token") Long hid, @RequestBody @Valid PostPhoneNumberRequestDTO body) {

        commandGateway.sendAndWait(new UpdatePhoneNumberCommand(hid, body.getPhoneNumber()));

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/language/update")
    public ResponseEntity<?> postLanguage(@RequestHeader(value = "hedvig.token") Long hid, @RequestBody @Valid PostLanguageRequestDTO body) {

        commandGateway.sendAndWait(new UpdateAcceptLanguageCommand(hid, body.getAcceptLanguage()));

        return ResponseEntity.accepted().build();
    }

  @PostMapping("/pickedLocale/update")
  public ResponseEntity<Member> postPickedLocale(@RequestHeader(value = "hedvig.token") Long hid, @RequestBody @Valid PostPickedLocaleRequestDTO body) {

    commandGateway.sendAndWait(new UpdatePickedLocaleCommand(hid, body.getPickedLocale()));

    Optional<MemberEntity> member = repo.findById(hid);
    if (member.isPresent()) {

      return ResponseEntity.ok(new Member(member.get()));
    }

    return ResponseEntity.notFound().build();

  }
}
