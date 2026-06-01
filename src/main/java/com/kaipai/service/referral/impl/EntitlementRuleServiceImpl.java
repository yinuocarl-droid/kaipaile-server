package com.kaipai.service.referral.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.model.referral.entity.EntitlementRule;
import com.kaipai.mapper.referral.EntitlementRuleMapper;
import com.kaipai.service.referral.EntitlementRuleService;
import org.springframework.stereotype.Service;

@Service
public class EntitlementRuleServiceImpl extends ServiceImpl<EntitlementRuleMapper, EntitlementRule> implements EntitlementRuleService {
}
