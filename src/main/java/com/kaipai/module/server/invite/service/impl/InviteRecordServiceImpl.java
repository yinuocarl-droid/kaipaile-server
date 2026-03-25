package com.kaipai.module.server.invite.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.invite.entity.InviteRecord;
import com.kaipai.module.server.invite.mapper.InviteRecordMapper;
import com.kaipai.module.server.invite.service.InviteRecordService;
import org.springframework.stereotype.Service;

@Service
public class InviteRecordServiceImpl extends ServiceImpl<InviteRecordMapper, InviteRecord> implements InviteRecordService {
}
