package com.hedvig.memberservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedvig.external.billectaAPI.BillectaApi;
import com.hedvig.memberservice.commands.MemberUpdateContactInformationCommand;
import com.hedvig.memberservice.commands.SelectNewCashbackCommand;
import com.hedvig.memberservice.commands.StartOnboardingWithSSNCommand;
import com.hedvig.memberservice.externalApi.BotService;
import com.hedvig.memberservice.query.CollectRepository;
import com.hedvig.memberservice.query.MemberEntity;
import com.hedvig.memberservice.query.MemberRepository;
import com.hedvig.memberservice.services.CashbackService;
import com.hedvig.memberservice.web.CashbackController;
import com.hedvig.memberservice.web.InternalMembersController;
import com.hedvig.memberservice.web.dto.Address;
import com.hedvig.memberservice.web.dto.CashbackOption;
import com.hedvig.memberservice.web.dto.StartOnboardingWithSSNRequest;
import com.hedvig.memberservice.web.dto.UpdateContactInformationRequest;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.spring.config.EnableAxon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestApplication.class)
@WebMvcTest(controllers = CashbackController.class)
@EnableAxon
public class CachBackControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    MemberRepository memberRepo;

    @MockBean
    CommandGateway commandGateway;

    @MockBean
    CashbackService cashbackService;



    @Test
    public void PostCachbackOption() throws Exception {
        final long memberId = 1337l;

        MemberEntity member = new MemberEntity();
        member.setId(memberId);
        final String newOptionId = UUID.fromString("d24c427e-d110-11e7-a47e-0b4e39412e98");

        new CashbackOption();

        when(cashbackService.getCashbackOption(UUID.fromString(newOptionId))).thenReturn(Optional.of());
        when(memberRepo.findById(memberId)).thenReturn(Optional.of(member));

        StartOnboardingWithSSNRequest request = new StartOnboardingWithSSNRequest("");



        mockMvc.perform(
                post("/cashback", "").param("optionId", newOptionId).header("hedvig.token", memberId)).
                andExpect(status().isNoContent());

        verify(commandGateway, times(1)).sendAndWait(new SelectNewCashbackCommand(memberId, UUID.fromString(newOptionId)));
    }

    @Test
    public void PostCachbackOption_WHEN_OptionId_IsnotFound() throws Exception {
        final long memberId = 1337l;

        MemberEntity member = new MemberEntity();
        member.setId(memberId);

        when(memberRepo.findById(memberId)).thenReturn(Optional.of(member));

        StartOnboardingWithSSNRequest request = new StartOnboardingWithSSNRequest("");

        final String newOptionId = "d24c427e-d110-11e7-a47e-0b4e39412e99";

        mockMvc.perform(
                post("/cashback", "").param("optionId", newOptionId).header("hedvig.token", memberId)).
                andExpect(status().isNotFound());

        verify(commandGateway, times(0)).sendAndWait(new SelectNewCashbackCommand(memberId, UUID.fromString(newOptionId)));
    }

    @Test
    public void PostCachbackOption_WHEN_member_IsnotFound() throws Exception {
        final long memberId = 1337l;

        MemberEntity member = new MemberEntity();
        member.setId(memberId);

        when(memberRepo.findById(memberId)).thenReturn(Optional.empty());

        StartOnboardingWithSSNRequest request = new StartOnboardingWithSSNRequest("");

        final String newOptionId = "d24c427e-d110-11e7-a47e-0b4e39412e98";

        mockMvc.perform(
                post("/cashback", "").param("optionId", newOptionId).header("hedvig.token", memberId)).
                andExpect(status().isNotFound());

        verify(commandGateway, times(0)).sendAndWait(new SelectNewCashbackCommand(memberId, UUID.fromString(newOptionId)));
    }

}
*/
