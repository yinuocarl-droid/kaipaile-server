package com.kaipai.module.server.referral.service;

import com.kaipai.module.model.auth.dto.RegisterReqDTO;
import com.kaipai.module.model.user.entity.User;

public interface ReferralRegistrationService {

    void bindInviteOnRegister(User user, RegisterReqDTO registerReq);
}
