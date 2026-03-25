package com.kaipai.module.server.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.user.entity.CreditScoreLog;
import com.kaipai.module.server.user.mapper.CreditScoreLogMapper;
import com.kaipai.module.server.user.service.CreditScoreLogService;
import org.springframework.stereotype.Service;

@Service
public class CreditScoreLogServiceImpl extends ServiceImpl<CreditScoreLogMapper, CreditScoreLog> implements CreditScoreLogService {
}
