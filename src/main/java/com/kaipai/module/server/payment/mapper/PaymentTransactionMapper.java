package com.kaipai.module.server.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.module.model.payment.entity.PaymentTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {
}
