package com.kaipai.module.invite.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.invite.entity.InviteRecord;
import com.kaipai.module.invite.mapper.InviteRecordMapper;
import com.kaipai.module.invite.service.InviteRecordService;
import org.springframework.stereotype.Service;

@Service
public class InviteRecordServiceImpl extends ServiceImpl<InviteRecordMapper, InviteRecord> implements InviteRecordService {
}
