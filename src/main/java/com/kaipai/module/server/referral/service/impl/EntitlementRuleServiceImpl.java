package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.referral.entity.EntitlementRule;
import com.kaipai.module.server.referral.mapper.EntitlementRuleMapper;
import com.kaipai.module.server.referral.service.EntitlementRuleService;
import org.springframework.stereotype.Service;

@Service
public class EntitlementRuleServiceImpl extends ServiceImpl<EntitlementRuleMapper, EntitlementRule> implements EntitlementRuleService {
}
