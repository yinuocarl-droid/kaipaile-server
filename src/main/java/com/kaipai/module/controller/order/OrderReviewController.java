package com.kaipai.module.controller.order;

import com.kaipai.module.server.order.service.OrderReviewService;
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
