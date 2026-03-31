package com.kaipai.module.server.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
