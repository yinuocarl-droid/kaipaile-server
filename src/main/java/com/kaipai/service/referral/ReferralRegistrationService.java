package com.kaipai.service.referral;

import com.kaipai.model.auth.dto.RegisterReqDTO;
import com.kaipai.model.referral.entity.InviteCode;
import com.kaipai.model.user.entity.User;

public interface ReferralRegistrationService {

    InviteCode prepareInviteOnRegister(User user, RegisterReqDTO registerReq);

    void persistInviteOnRegister(User user, InviteCode inviteCode);
}
