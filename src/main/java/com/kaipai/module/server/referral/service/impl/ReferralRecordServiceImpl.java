package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.referral.entity.ReferralRecord;
import com.kaipai.module.server.referral.mapper.ReferralRecordMapper;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import org.springframework.stereotype.Service;

@Service
public class ReferralRecordServiceImpl extends ServiceImpl<ReferralRecordMapper, ReferralRecord> implements ReferralRecordService {
}
