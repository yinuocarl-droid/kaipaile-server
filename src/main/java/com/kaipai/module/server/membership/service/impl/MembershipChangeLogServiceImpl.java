package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;
import com.kaipai.module.server.membership.mapper.MembershipChangeLogMapper;
import com.kaipai.module.server.membership.service.MembershipChangeLogService;
import org.springframework.stereotype.Service;

@Service
public class MembershipChangeLogServiceImpl extends ServiceImpl<MembershipChangeLogMapper, MembershipChangeLog> implements MembershipChangeLogService {
}
