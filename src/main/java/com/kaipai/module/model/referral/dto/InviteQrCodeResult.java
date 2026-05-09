package com.kaipai.module.model.referral.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InviteQrCodeResult {

    private String dataUrl;

    private String qrCodeType;

    private String scene;

    private String page;
}
