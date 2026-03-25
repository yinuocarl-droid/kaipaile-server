package com.kaipai.module.controller.order;

import com.kaipai.module.server.order.service.CooperationOrderService;
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
