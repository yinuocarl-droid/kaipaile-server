package com.kaipai.mapper.payment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
