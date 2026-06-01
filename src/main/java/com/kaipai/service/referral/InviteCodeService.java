package com.kaipai.service.referral;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.model.referral.entity.InviteCode;

public interface InviteCodeService extends IService<InviteCode> {

    InviteCode ensureActiveInviteCode(Long userId);
}
