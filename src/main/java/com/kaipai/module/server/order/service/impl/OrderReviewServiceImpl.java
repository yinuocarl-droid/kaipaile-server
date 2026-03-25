package com.kaipai.module.server.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.order.entity.OrderReview;
import com.kaipai.module.server.order.mapper.OrderReviewMapper;
import com.kaipai.module.server.order.service.OrderReviewService;
import org.springframework.stereotype.Service;

@Service
public class OrderReviewServiceImpl extends ServiceImpl<OrderReviewMapper, OrderReview> implements OrderReviewService {
}
