package com.kaipai.module.server.referral.service;

import com.kaipai.module.model.referral.dto.InviteQrCodeResult;

public interface InviteQrCodeService {

    InviteQrCodeResult buildInviteQrCode(String inviteCode);
}
