package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.referral.entity.ReferralPolicy;
import com.kaipai.module.server.referral.mapper.ReferralPolicyMapper;
import com.kaipai.module.server.referral.service.ReferralPolicyService;
import org.springframework.stereotype.Service;

@Service
public class ReferralPolicyServiceImpl extends ServiceImpl<ReferralPolicyMapper, ReferralPolicy> implements ReferralPolicyService {
}
