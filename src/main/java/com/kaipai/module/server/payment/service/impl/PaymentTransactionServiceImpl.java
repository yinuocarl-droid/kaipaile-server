package com.kaipai.module.server.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.payment.entity.PaymentTransaction;
import com.kaipai.module.server.payment.mapper.PaymentTransactionMapper;
import com.kaipai.module.server.payment.service.PaymentTransactionService;
import org.springframework.stereotype.Service;

@Service
public class PaymentTransactionServiceImpl extends ServiceImpl<PaymentTransactionMapper, PaymentTransaction> implements PaymentTransactionService {
}
