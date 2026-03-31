package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.system.entity.AdminOperationLog;
import com.kaipai.module.server.system.mapper.AdminOperationLogMapper;
import com.kaipai.module.server.system.service.AdminOperationLogService;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationLogServiceImpl extends ServiceImpl<AdminOperationLogMapper, AdminOperationLog> implements AdminOperationLogService {
}
