package com.kaipai.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.user.entity.CreditScore;
import com.kaipai.module.user.mapper.CreditScoreMapper;
import com.kaipai.module.user.service.CreditScoreService;
import org.springframework.stereotype.Service;

@Service
public class CreditScoreServiceImpl extends ServiceImpl<CreditScoreMapper, CreditScore> implements CreditScoreService {
}
