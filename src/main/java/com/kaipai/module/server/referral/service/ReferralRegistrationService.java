package com.kaipai.module.server.referral.service;

import com.kaipai.module.model.auth.dto.RegisterReqDTO;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.model.user.entity.User;

public interface ReferralRegistrationService {

    InviteCode prepareInviteOnRegister(User user, RegisterReqDTO registerReq);

    void persistInviteOnRegister(User user, InviteCode inviteCode);
}
