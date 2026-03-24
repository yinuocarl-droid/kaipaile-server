package com.kaipai.module.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.order.entity.CooperationOrder;
import com.kaipai.module.order.mapper.CooperationOrderMapper;
import com.kaipai.module.order.service.CooperationOrderService;
import org.springframework.stereotype.Service;

@Service
public class CooperationOrderServiceImpl extends ServiceImpl<CooperationOrderMapper, CooperationOrder> implements CooperationOrderService {
}
