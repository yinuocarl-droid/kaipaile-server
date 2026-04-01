package com.kaipai.module.model.referral.dto;

import lombok.Data;

@Data
public class ActorInviteStatsRespDTO {

    private Integer validInviteCount;

    private Integer totalInviteCount;

    private Integer pendingInviteCount;

    private Integer flaggedInviteCount;
}
