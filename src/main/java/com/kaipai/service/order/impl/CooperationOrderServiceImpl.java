package com.kaipai.service.order.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.model.order.entity.CooperationOrder;
import com.kaipai.mapper.order.CooperationOrderMapper;
import com.kaipai.service.order.CooperationOrderService;
import org.springframework.stereotype.Service;

@Service
public class CooperationOrderServiceImpl extends ServiceImpl<CooperationOrderMapper, CooperationOrder> implements CooperationOrderService {
}
