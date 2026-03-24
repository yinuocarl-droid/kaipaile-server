package com.kaipai.module.order.controller;

import com.kaipai.module.order.service.OrderReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单评价管理")
@RestController
@RequestMapping("/order/review")
@RequiredArgsConstructor
public class OrderReviewController {

    private final OrderReviewService orderReviewService;
}
