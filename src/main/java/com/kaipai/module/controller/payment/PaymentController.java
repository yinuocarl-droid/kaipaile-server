package com.kaipai.module.controller.payment;

import com.kaipai.module.server.payment.service.PaymentOrderService;
import com.kaipai.module.server.payment.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "会员支付")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentTransactionService paymentTransactionService;
}
