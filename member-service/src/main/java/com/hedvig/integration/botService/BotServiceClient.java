package com.hedvig.integration.botService;

import com.hedvig.integration.botService.dto.UpdateUserContextDTO;
import com.hedvig.integration.productsPricing.dto.EditMemberNameRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
  name = "bot-service",
  url = "${hedvig.bot-service.url:bot-service}"
)
public interface BotServiceClient {

  @PostMapping("/_/member/{memberId}/initSessionWebOnBoarding")
  ResponseEntity<?> initBotServiceSessionWebOnBoarding(@PathVariable(name = "memberId") Long memberId, @RequestBody UpdateUserContextDTO req);

  @PostMapping("/_/member/{memberId}/editMemberName")
  ResponseEntity<EditMemberNameRequestDTO> editMemberName(
    @PathVariable("memberId") String memberId,
    @RequestBody EditMemberNameRequestDTO dto
  );

  @PostMapping("/_/messages/init")
  ResponseEntity<?> initBotService(@RequestHeader(name = "hedvig.token") Long memberId, @RequestBody(required = false) String json);

  @PostMapping("/_/messages/init")
  ResponseEntity<?> initBotService(@RequestHeader(name = "hedvig.token") Long memberId);
}
