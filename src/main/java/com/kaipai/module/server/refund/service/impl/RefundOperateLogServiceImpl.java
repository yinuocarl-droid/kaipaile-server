package com.kaipai.module.server.refund.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.refund.entity.RefundOperateLog;
import com.kaipai.module.server.refund.mapper.RefundOperateLogMapper;
import com.kaipai.module.server.refund.service.RefundOperateLogService;
import org.springframework.stereotype.Service;

@Service
public class RefundOperateLogServiceImpl extends ServiceImpl<RefundOperateLogMapper, RefundOperateLog> implements RefundOperateLogService {
}
