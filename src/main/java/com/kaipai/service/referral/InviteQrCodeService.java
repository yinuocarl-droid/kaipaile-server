package com.kaipai.service.referral;

import com.kaipai.model.referral.dto.InviteQrCodeResult;

public interface InviteQrCodeService {

    InviteQrCodeResult buildInviteQrCode(String inviteCode);
}
