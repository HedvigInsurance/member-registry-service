package com.hedvig.memberservice.events;

import com.hedvig.memberservice.aggregates.MemberStatus;
import com.hedvig.memberservice.aggregates.PersonInformation;
import lombok.Value;

@Value
public class MemberStartedOnBoardingEvent {
    private Long memberId;
    private MemberStatus newStatus;
    private PersonInformation personInformation;
}