package com.kaipai.module.server.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.order.entity.CooperationOrder;
import com.kaipai.module.server.order.mapper.CooperationOrderMapper;
import com.kaipai.module.server.order.service.CooperationOrderService;
import org.springframework.stereotype.Service;

@Service
public class CooperationOrderServiceImpl extends ServiceImpl<CooperationOrderMapper, CooperationOrder> implements CooperationOrderService {
}
