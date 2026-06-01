package com.kaipai.mapper.payment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.payment.entity.PaymentTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {
}
