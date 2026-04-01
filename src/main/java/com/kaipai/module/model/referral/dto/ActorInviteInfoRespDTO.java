package com.kaipai.module.model.referral.dto;

import lombok.Data;

@Data
public class ActorInviteInfoRespDTO {

    private String inviteCode;

    private String inviteLink;

    private String qrCodeUrl;

    private Integer validInviteCount;

    private Integer totalInviteCount;

    private Integer pendingInviteCount;

    private Integer flaggedInviteCount;
}
