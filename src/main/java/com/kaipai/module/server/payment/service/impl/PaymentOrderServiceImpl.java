package com.kaipai.module.server.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.payment.service.PaymentOrderService;
import org.springframework.stereotype.Service;

@Service
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements PaymentOrderService {
}
