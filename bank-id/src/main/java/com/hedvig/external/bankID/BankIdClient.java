package com.hedvig.external.bankID;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import javax.xml.bind.JAXBElement;

import bankid.AuthenticateRequestType;
import bankid.ObjectFactory;
import bankid.OrderResponseType;
import bankid.SignRequestType;
import bankid.CollectResponseType;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.ws.client.core.WebServiceTemplate;

@Service
public class BankIdClient {

  private final WebServiceTemplate webServiceTemplate;

  public BankIdClient(WebServiceTemplate webServiceTemplate) {

    this.webServiceTemplate = webServiceTemplate;
  }

  public OrderResponseType auth(String ssn) {

    ObjectFactory factory = new ObjectFactory();
    AuthenticateRequestType t = factory.createAuthenticateRequestType();
    t.setPersonalNumber(ssn);
    JAXBElement<AuthenticateRequestType> authenticateRequest = factory.createAuthenticateRequest(t);
    // WebServiceTemplate webServiceTemplate = this.getWebServiceTemplate();

    JAXBElement<OrderResponseType> type =
        (JAXBElement<OrderResponseType>)
            webServiceTemplate.marshalSendAndReceive(authenticateRequest);

    return type.getValue();
  }

  public OrderResponseType sign(String ssn, String message) throws UnsupportedEncodingException {

    ObjectFactory factory = new ObjectFactory();
    SignRequestType t = factory.createSignRequestType();
    t.setPersonalNumber(ssn);
    String encodedMessage = Base64Utils.encodeToString(message.getBytes(StandardCharsets.UTF_8));
    System.out.println(encodedMessage.length());

    t.setUserVisibleData(encodedMessage);
    JAXBElement<SignRequestType> authenticateRequest = factory.createSignRequest(t);

    JAXBElement<OrderResponseType> type =
        (JAXBElement<OrderResponseType>)
            webServiceTemplate.marshalSendAndReceive(authenticateRequest);

    return type.getValue();
  }

  public CollectResponseType collect(String referenceToken) {
    ObjectFactory factory = new ObjectFactory();

    JAXBElement<CollectResponseType> type =
        (JAXBElement<CollectResponseType>)
            webServiceTemplate.marshalSendAndReceive(factory.createOrderRef(referenceToken));

    return type.getValue();
  }
}
