package com.kaipai.module.model.referral.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActorReferralRecordRespDTO {

    private Long id;

    private String inviteeNickname;

    private LocalDateTime registeredAt;

    @JsonProperty("isValid")
    private Boolean isValid;

    private Boolean flagged;

    private LocalDateTime validatedAt;
}
