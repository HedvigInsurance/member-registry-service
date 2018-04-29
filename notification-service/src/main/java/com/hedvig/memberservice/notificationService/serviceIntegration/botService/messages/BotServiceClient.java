package com.hedvig.memberservice.notificationService.serviceIntegration.botService.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.hedvig.memberservice.notificationService.serviceIntegration.botService.messages.dto.BackOfficeMessage;
import com.hedvig.memberservice.notificationService.serviceIntegration.botService.messages.dto.BackOfficeResponseDTO;
import com.hedvig.memberservice.notificationService.serviceIntegration.botService.messages.dto.PushTokenDTO;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "bot-service",
        url = "${hedvig.bot-service.url}")
public interface BotServiceClient {

    @GetMapping("/_/member/{hid}/push-token")
    PushTokenDTO getPushTokenByHid(@PathVariable("hid") String hid, @RequestHeader("Authorization") String token);

    @GetMapping("/messages")
    JsonNode messages(@RequestHeader("hedvig.token") String hid, @RequestHeader("Authorization") String token);

    @GetMapping("/messages/{count}")
    JsonNode messages(@RequestHeader("hedvig.token") String hid, @PathVariable("count") int count, @RequestHeader("Authorization") String token);

    @GetMapping("/_/messages/{time}")
    List<BackOfficeMessage> fetch(@PathVariable("time") long time, @RequestHeader("Authorization") String token);

    @PostMapping("/_/messages/addmessage")
    void response(@RequestBody BackOfficeResponseDTO message, @RequestHeader("Authorization") String token);

    @PostMapping("/_/messages/addanswer")
    void answer(@RequestBody BackOfficeResponseDTO answer, @RequestHeader("Authorization") String token);
}