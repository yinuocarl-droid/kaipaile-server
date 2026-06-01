package com.kaipai.service.order.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.model.order.entity.OrderReview;
import com.kaipai.mapper.order.OrderReviewMapper;
import com.kaipai.service.order.OrderReviewService;
import org.springframework.stereotype.Service;

@Service
public class OrderReviewServiceImpl extends ServiceImpl<OrderReviewMapper, OrderReview> implements OrderReviewService {
}
