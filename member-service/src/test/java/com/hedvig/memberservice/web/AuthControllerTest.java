package com.hedvig.memberservice.web;

import com.hedvig.external.bankID.bankIdTypes.BankIdError;
import com.hedvig.external.bankID.bankIdTypes.BankIdErrorType;
import com.hedvig.external.bankID.bankIdTypes.Collect.User;
import com.hedvig.external.bankID.bankIdTypes.CollectResponse;
import com.hedvig.external.bankID.bankIdTypes.CollectStatus;
import com.hedvig.external.bankID.bankIdTypes.CompletionData;
import com.hedvig.memberservice.commands.AuthenticationAttemptCommand;
import com.hedvig.memberservice.commands.BankIdAuthenticationStatus;
import com.hedvig.memberservice.commands.BankIdSignCommand;
import com.hedvig.memberservice.query.CollectRepository;
import com.hedvig.memberservice.query.CollectType;
import com.hedvig.memberservice.query.MemberEntity;
import com.hedvig.memberservice.query.MemberRepository;
import com.hedvig.memberservice.query.SignedMemberEntity;
import com.hedvig.memberservice.query.SignedMemberRepository;
import com.hedvig.memberservice.services.BankIdService;
import com.hedvig.memberservice.services.NorwegianBankIdService;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = AuthController.class)
public class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  MemberRepository memberRepo;

  @MockBean
  CommandGateway commandGateway;

  @MockBean
  BankIdService bankIdService;

  @MockBean
  CollectRepository collectRepo;

  @MockBean
  SignedMemberRepository signedMemberRepository;

  @MockBean
  NorwegianBankIdService norwegianBankIdService;

  @Test
  public void collect_referenceTokenNotFound_results_in_500() throws Exception {
    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", "someReferenceValue").
          header("hedvig.token", "1337")
      ).
      andExpect(status().isInternalServerError());

  }

  @Captor
  private ArgumentCaptor<AuthenticationAttemptCommand> captor;

  @Test
  public void collect_Auth_status_COMPLETE() throws Exception {

    final Long memberId = 1337L;
    final String someReferenceValue = "someReferenceValue";
    final String ssn = "1212121212";

    CollectType collectType = new CollectType();
    collectType.type = CollectType.RequestType.AUTH;
    collectType.token = someReferenceValue;
    collectType.memberId = memberId;

    when(collectRepo.findById(someReferenceValue)).thenReturn(Optional.of(collectType));

    CollectResponse collectResponse = createCompletCollecteResponse(ssn, someReferenceValue);

    when(bankIdService.authCollect(someReferenceValue)).thenReturn(collectResponse);
    when(signedMemberRepository.findBySsn(ssn)).thenReturn(Optional.empty());

    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", someReferenceValue).
          header("hedvig.token", memberId.toString())
      ).
      andExpect(
        status().is2xxSuccessful()).
      andExpect(jsonPath("$.bankIdStatus", is("COMPLETE"))).
      andExpect(jsonPath("$.referenceToken", is(someReferenceValue))).
      andExpect(jsonPath("$.newMemberId", is(memberId.toString())));

    BankIdAuthenticationStatus authStatus = new BankIdAuthenticationStatus(ssn, someReferenceValue, "", "");
    verify(commandGateway).sendAndWait(captor.capture());

    AuthenticationAttemptCommand sentCommand = captor.getValue();
    assertThat(sentCommand.getId()).isEqualTo(memberId);
    assertThat(sentCommand.getBankIdAuthResponse().getReferenceToken()).isEqualTo(someReferenceValue);
    assertThat(sentCommand.getBankIdAuthResponse().getSSN()).isEqualTo(ssn);
  }

  @Test
  public void collect_Auth_ExistingMember_returnsNew_memberId() throws Exception {

    final Long memberId = 1337L;
    final String someReferenceValue = "someReferenceValue";
    final String ssn = "1212121212";

    final Long existingMemberId = 1338L;

    CollectType collectType = new CollectType();
    collectType.type = CollectType.RequestType.AUTH;
    collectType.token = someReferenceValue;
    collectType.memberId = memberId;

    when(collectRepo.findById(someReferenceValue)).thenReturn(Optional.of(collectType));

    CollectResponse collectResponse = createCompletCollecteResponse(ssn, someReferenceValue);

    when(bankIdService.authCollect(someReferenceValue)).thenReturn(collectResponse);

    SignedMemberEntity signedMemberEntity = new SignedMemberEntity();
    signedMemberEntity.setSsn(ssn);
    signedMemberEntity.setId(existingMemberId);
    when(signedMemberRepository.findBySsn(ssn)).thenReturn(Optional.of(signedMemberEntity));

    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", someReferenceValue).
          header("hedvig.token", memberId.toString())
      ).
      andExpect(
        status().is2xxSuccessful()).
      andExpect(jsonPath("$.bankIdStatus", is("COMPLETE"))).
      andExpect(jsonPath("$.referenceToken", is(someReferenceValue))).
      andExpect(jsonPath("$.newMemberId", is(existingMemberId.toString())));

  }


  @Test
  public void collect_Auth_BankIDError() throws Exception {

    final Long memberId = 1337L;
    final String someReferenceValue = "someReferenceValue";

    CollectType collectType = new CollectType();
    collectType.type = CollectType.RequestType.AUTH;
    collectType.token = someReferenceValue;
    collectType.memberId = memberId;

    when(collectRepo.findById(someReferenceValue)).thenReturn(Optional.of(collectType));

    when(bankIdService.authCollect(someReferenceValue)).thenThrow(new BankIdError(BankIdErrorType.INTERNAL_ERROR));

    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", someReferenceValue).
          header("hedvig.token", memberId.toString())
      ).
      andExpect(
        status().is5xxServerError()).
      andExpect(jsonPath("$.apiError").exists()).
      andExpect(jsonPath("$.apiError.code").value("INTERNAL_ERROR"));
  }


  @Test
  public void collect_Auth_status_STARTED() throws Exception {

    final Long memberId = 1337L;
    final String someReferenceValue = "someReferenceValue";
    final String ssn = "1212121212";

    CollectType collectType = new CollectType();
    collectType.type = CollectType.RequestType.AUTH;
    collectType.token = someReferenceValue;
    collectType.memberId = memberId;

    when(collectRepo.findById(someReferenceValue)).thenReturn(Optional.of(collectType));

    CollectResponse collectResponse = createStartedCollectResponse(ssn, someReferenceValue);

    when(bankIdService.authCollect(someReferenceValue)).thenReturn(collectResponse);

    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", someReferenceValue).
          header("hedvig.token", memberId.toString())
      ).
      andExpect(
        status().is2xxSuccessful()).
      andExpect(jsonPath("$.bankIdStatus", is("STARTED"))).
      andExpect(jsonPath("$.referenceToken", is(someReferenceValue))).
      andExpect(jsonPath("$.newMemberId", is(memberId.toString())));

  }


  @Test
  public void collect_SIGN_status_STARTED() throws Exception {

    final Long memberId = 1337L;
    final String someReferenceValue = "someReferenceValue";
    final String ssn = "1212121212";

    CollectType collectType = new CollectType();
    collectType.type = CollectType.RequestType.SIGN;
    collectType.token = someReferenceValue;
    collectType.memberId = memberId;

    when(collectRepo.findById(someReferenceValue)).thenReturn(Optional.of(collectType));

    CollectResponse collectResponse = createStartedCollectResponse(ssn, someReferenceValue);

    when(bankIdService.signCollect(someReferenceValue)).thenReturn(collectResponse);

    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", someReferenceValue).
          header("hedvig.token", memberId.toString())
      ).
      andExpect(
        status().is2xxSuccessful()).
      andExpect(jsonPath("$.bankIdStatus", is("STARTED"))).
      andExpect(jsonPath("$.referenceToken", is(someReferenceValue))).
      andExpect(jsonPath("$.newMemberId", is(memberId.toString())));

  }

  @Test
  public void collect_SIGN_status_COMPLETE_Sends_SIGN_COMMAND() throws Exception {

    final Long memberId = 1337L;
    final String someReferenceValue = "someReferenceValue";
    final String ssn = "1212121212";

    CollectType collectType = new CollectType();
    collectType.type = CollectType.RequestType.SIGN;
    collectType.token = someReferenceValue;
    collectType.memberId = memberId;

    when(collectRepo.findById(someReferenceValue)).thenReturn(Optional.of(collectType));

    CollectResponse collectResponse = createCompletCollecteResponse(ssn, someReferenceValue);

    when(bankIdService.signCollect(someReferenceValue)).thenReturn(collectResponse);

    MemberEntity memberEntity = new MemberEntity();
    memberEntity.setId(memberId);
    when(memberRepo.findById(memberId)).thenReturn(Optional.of(memberEntity));

    mockMvc.
      perform(
        post("/member/bankid/collect", "").
          param("referenceToken", someReferenceValue).
          header("hedvig.token", memberId.toString())
      ).
      andExpect(
        status().is2xxSuccessful()).
      andExpect(jsonPath("$.bankIdStatus", is("COMPLETE"))).
      andExpect(jsonPath("$.referenceToken", is(someReferenceValue))).
      andExpect(jsonPath("$.newMemberId", is(memberId.toString())));

    verify(commandGateway).sendAndWait(new BankIdSignCommand(memberId, someReferenceValue, "signature", "oscpResponse", ssn));

  }

  private CollectResponse createCompletCollecteResponse(String ssn, String orderReference) throws DatatypeConfigurationException {

    User user = new User(ssn, "Name", "GivenName", "Surname");

    CompletionData completionData = new CompletionData(user, null, null, "signature", "oscpResponse");

    return new CollectResponse(orderReference, CollectStatus.complete, null, completionData);
  }

  private CollectResponse createStartedCollectResponse(String ssn, String orderReference) throws DatatypeConfigurationException {
    return new CollectResponse(orderReference, CollectStatus.pending, "started", null);
  }


  private XMLGregorianCalendar createXMLGregorian(ZonedDateTime dateTime) throws DatatypeConfigurationException {
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(dateTime));
  }

}
