package com.hedvig.generic.customerregistry.web.dto;

import com.hedvig.external.billectaAPI.api.BankIdStatusType;
import lombok.Value;

@Value
public class BankIdAuthResponse {
    private BankIdStatusType bankIdStatus;
    private String autoStartToken;
    private String referenceToken;
}