package com.kaipai.module.order.controller;

import com.kaipai.module.order.service.CooperationOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "合作订单管理")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class CooperationOrderController {

    private final CooperationOrderService cooperationOrderService;
}
