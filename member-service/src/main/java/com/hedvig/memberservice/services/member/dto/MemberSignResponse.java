package com.hedvig.memberservice.services.member.dto;

import com.hedvig.external.bankID.bankIdRestTypes.OrderResponse;
import com.hedvig.memberservice.enteties.SignStatus;
import lombok.Value;
import org.springframework.lang.NonNull;

@Value
public class MemberSignResponse {

  @NonNull
  SignStatus status;

  @NonNull
  OrderResponse bankIdOrderResponse;

}
