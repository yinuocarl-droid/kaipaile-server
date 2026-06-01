package com.kaipai.model.referral.dto;

import lombok.Data;

@Data
public class ActorInviteInfoRespDTO {

    private String inviteCode;

    private String inviteLink;

    private String qrCodeUrl;

    private String qrCodeType;

    private String qrCodeScene;

    private String qrCodePage;

    private Integer validInviteCount;

    private Integer totalInviteCount;

    private Integer pendingInviteCount;

    private Integer flaggedInviteCount;
}
