package com.kaipai.controller.api.refund;

import com.kaipai.service.refund.RefundOrderService;
import com.kaipai.service.refund.RefundOperateLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "退款中心")
@RestController
@RequestMapping("/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundOrderService refundOrderService;
    private final RefundOperateLogService refundOperateLogService;
}
