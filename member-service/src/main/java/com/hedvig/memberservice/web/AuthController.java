package com.hedvig.memberservice.web;

import static net.logstash.logback.argument.StructuredArguments.value;

import com.hedvig.external.bankID.bankIdRestTypes.CollectResponse;
import com.hedvig.external.bankID.bankIdRestTypes.CollectStatus;
import com.hedvig.external.bankID.bankIdRestTypes.OrderResponse;
import com.hedvig.memberservice.aggregates.exceptions.BankIdReferenceUsedException;
import com.hedvig.memberservice.commands.AuthenticationAttemptCommand;
import com.hedvig.memberservice.commands.BankIdAuthenticationStatus;
import com.hedvig.memberservice.commands.BankIdSignCommand;
import com.hedvig.memberservice.commands.InactivateMemberCommand;
import com.hedvig.memberservice.query.CollectRepository;
import com.hedvig.memberservice.query.CollectType;
import com.hedvig.memberservice.query.MemberEntity;
import com.hedvig.memberservice.query.MemberRepository;
import com.hedvig.memberservice.query.SignedMemberEntity;
import com.hedvig.memberservice.query.SignedMemberRepository;
import com.hedvig.memberservice.services.BankIdService;
import com.hedvig.memberservice.web.dto.BankIdAuthRequest;
import com.hedvig.memberservice.web.dto.BankIdAuthResponse;
import com.hedvig.memberservice.web.dto.BankIdCollectResponse;
import com.hedvig.memberservice.web.dto.BankIdProgressStatus;
import com.hedvig.memberservice.web.dto.BankIdSignRequest;
import com.hedvig.memberservice.web.dto.BankIdSignResponse;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.Optional;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/member/bankid/")
public class AuthController {

  private static Logger log = LoggerFactory.getLogger(AuthController.class);
  private final CommandGateway commandGateway;
  private final MemberRepository memberRepo;
  private final SignedMemberRepository signedMemberRepository;
  private final CollectRepository collectRepo;
  private final BankIdService bankIdService;

  @Autowired
  public AuthController(
      CommandGateway commandGateway,
      MemberRepository memberRepo,
      SignedMemberRepository signedMemberRepository,
      CollectRepository collectRepo,
      BankIdService bankIdService) {
    this.commandGateway = commandGateway;
    this.memberRepo = memberRepo;
    this.signedMemberRepository = signedMemberRepository;
    this.collectRepo = collectRepo;
    this.bankIdService = bankIdService;
  }

  @PostMapping(path = "auth")
  public ResponseEntity<BankIdAuthResponse> auth(@RequestHeader(value = "x-forwarded-for", required = false) String forwardFor, @RequestBody BankIdAuthRequest request) {

    log.info(
        "Auth request for with memberId: {}", request.getMemberId(), value("memberId", request.getMemberId()));

    long memberId = convertMemberId(request.getMemberId());

    OrderResponse status = bankIdService.auth(memberId, forwardFor);
    BankIdAuthResponse response =
        new BankIdAuthResponse(status.getAutoStartToken(), status.getOrderRef());

    return ResponseEntity.ok(response);
  }

  @PostMapping(path = "sign")
  public ResponseEntity<BankIdSignResponse> sign(@RequestHeader(value = "x-forwarded-for", required = false) String forwardFor, @RequestBody BankIdSignRequest request)
      throws UnsupportedEncodingException {
    long memberId = convertMemberId(request.getMemberId());

    log.info(
        "Sign request for ssn: {}", request.getSsn(), value("memberId", request.getMemberId()));

    OrderResponse status = bankIdService.sign(request.getSsn(), request.getUserMessage(), memberId, forwardFor);
    BankIdSignResponse response =
        new BankIdSignResponse(status.getAutoStartToken(), status.getOrderRef());

    return ResponseEntity.ok(response);
  }

  private long convertMemberId(String memberId) {
    try {
      return Long.parseLong(memberId);
    } catch (Exception e) {
      throw new HttpMessageNotReadableException("Could not parse memberId");
    }
  }

  @Deprecated
  @PostMapping(path = "collect")
  public ResponseEntity<?> collect(
      @RequestParam String referenceToken,
      @RequestHeader(value = "hedvig.token")Long hid)
      throws InterruptedException {

    log.info("Start collect");

    CollectType collectType = collectRepo.findById(referenceToken).orElse(null);
    BankIdCollectResponse response;

    if (collectType == null) {
      log.error("ERROR: Oh no! Collect type is null!");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    if (collectType.type.equals(CollectType.RequestType.AUTH)) {
      CollectResponse status = bankIdService.authCollect(referenceToken);

      if (status.getStatus() == CollectStatus.complete) {
        String ssn = status.getCompletionData().getUser().getPersonalNumber();

        Optional<SignedMemberEntity> member = signedMemberRepository.findBySsn(ssn);

        Long currentMemberId = hid;
        if (member.isPresent()) {
          SignedMemberEntity m = member.get();
          if (!m.getId().equals(hid)) {
            this.commandGateway.sendAndWait(new InactivateMemberCommand(hid));
          }
          currentMemberId = m.getId();
        }

        try {
          BankIdAuthenticationStatus authStatus = new BankIdAuthenticationStatus();
          authStatus.setSSN(status.getCompletionData().getUser().getPersonalNumber());
          authStatus.setGivenName(status.getCompletionData().getUser().getGivenName());
          authStatus.setSurname(status.getCompletionData().getUser().getSurname());
          authStatus.setReferenceToken(referenceToken);
          this.commandGateway.sendAndWait(
              new AuthenticationAttemptCommand(currentMemberId, authStatus));
          Thread.sleep(1000L);
        } catch (BankIdReferenceUsedException e) {
          log.info("Old reference token used: ", e);
          return ResponseEntity.badRequest().body("{\"message\":\"" + e.getMessage() + "\"}");
        }

        response =
            new BankIdCollectResponse(
                BankIdProgressStatus.Companion.valueOf(status),
                referenceToken,
                Objects.toString(currentMemberId));

        return ResponseEntity.ok().header("Hedvig.Id", currentMemberId.toString()).body(response);
      }

      return ResponseEntity.ok(
          new BankIdCollectResponse(
              BankIdProgressStatus.Companion.valueOf(status),
              referenceToken,
              hid.toString()));

    } else if (collectType.type.equals(CollectType.RequestType.SIGN)) {
      CollectResponse status = bankIdService.signCollect(referenceToken);
      if (status.getStatus() == CollectStatus.complete) {
        Optional<MemberEntity> memberEntity = memberRepo.findById(hid);
        if (memberEntity.isPresent()) {
          this.commandGateway.sendAndWait(
              new
                  BankIdSignCommand(
                  hid, referenceToken, status.getCompletionData().getSignature(), status.getCompletionData().getOcspResponse(),
                  status.getCompletionData().getUser().getPersonalNumber()));
        }
      }

      return ResponseEntity.ok(
          new BankIdCollectResponse(
              BankIdProgressStatus.Companion.valueOf(status),
              referenceToken,
              hid.toString()));
    } else {
      return ResponseEntity.noContent().build();
    }
  }
}
