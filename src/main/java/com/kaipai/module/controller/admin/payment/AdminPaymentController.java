package com.kaipai.module.controller.admin.payment;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderQueryDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionQueryDTO;
import com.kaipai.module.server.payment.service.PaymentOrderService;
import com.kaipai.module.server.payment.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台支付")
@RestController
@RequestMapping("/admin/payment")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentTransactionService paymentTransactionService;

    @Operation(summary = "支付订单列表")
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('page.payment.orders')")
    public R<PageResult<AdminPaymentOrderListItemDTO>> orders(@Valid AdminPaymentOrderQueryDTO query) {
        return R.ok(paymentOrderService.adminOrderList(query));
    }

    @Operation(summary = "支付订单详情")
    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('page.payment.orders')")
    public R<AdminPaymentOrderDetailDTO> orderDetail(@PathVariable Long id) {
        return R.ok(paymentOrderService.adminOrderDetail(id));
    }

    @Operation(summary = "支付流水列表")
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('page.payment.transactions')")
    public R<PageResult<AdminPaymentTransactionListItemDTO>> transactions(@Valid AdminPaymentTransactionQueryDTO query) {
        return R.ok(paymentTransactionService.adminTransactionList(query));
    }

    @Operation(summary = "支付流水详情")
    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('page.payment.transactions')")
    public R<AdminPaymentTransactionDetailDTO> transactionDetail(@PathVariable Long id) {
        return R.ok(paymentTransactionService.adminTransactionDetail(id));
    }
}
