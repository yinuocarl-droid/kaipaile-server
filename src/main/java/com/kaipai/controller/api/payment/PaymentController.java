package com.kaipai.controller.api.payment;

import com.kaipai.service.payment.PaymentOrderService;
import com.kaipai.service.payment.PaymentTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "支付管理")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentTransactionService paymentTransactionService;
}
