package com.hedvig.memberservice.web;

import com.hedvig.external.bankID.bankIdTypes.BankIdError;
import com.hedvig.memberservice.web.dto.APIErrorDTO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BankIdError.class)
  protected ResponseEntity<?> handleException(BankIdError error) {
    HttpStatus code = HttpStatus.INTERNAL_SERVER_ERROR;
    APIErrorDTO apiErrorDTO = new APIErrorDTO(code, error.getType().toString(), "Calling bankID failed");
    return ResponseEntity.status(code).body(apiErrorDTO);
  }
}
