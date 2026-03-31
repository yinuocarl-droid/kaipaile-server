package com.kaipai.module.controller.admin.payment;

import com.kaipai.module.server.payment.service.PaymentOrderService;
import com.kaipai.module.server.payment.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台支付")
@RestController
@RequestMapping("/admin/payment")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentTransactionService paymentTransactionService;
}
