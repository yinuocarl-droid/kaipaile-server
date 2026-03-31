package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.server.referral.mapper.InviteCodeMapper;
import com.kaipai.module.server.referral.service.InviteCodeService;
import org.springframework.stereotype.Service;

@Service
public class InviteCodeServiceImpl extends ServiceImpl<InviteCodeMapper, InviteCode> implements InviteCodeService {
}
