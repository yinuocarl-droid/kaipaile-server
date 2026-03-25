package com.kaipai.module.server.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.user.entity.CreditScore;
import com.kaipai.module.server.user.mapper.CreditScoreMapper;
import com.kaipai.module.server.user.service.CreditScoreService;
import org.springframework.stereotype.Service;

@Service
public class CreditScoreServiceImpl extends ServiceImpl<CreditScoreMapper, CreditScore> implements CreditScoreService {
}
