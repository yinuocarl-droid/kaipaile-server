package com.kaipai.module.controller.admin.refund;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.refund.dto.RefundApproveDTO;
import com.kaipai.module.model.refund.dto.RefundOperateLogItemDTO;
import com.kaipai.module.model.refund.dto.RefundOperateLogQueryDTO;
import com.kaipai.module.model.refund.dto.RefundOrderDetailDTO;
import com.kaipai.module.model.refund.dto.RefundOrderQueryDTO;
import com.kaipai.module.model.refund.dto.RefundOrderRespDTO;
import com.kaipai.module.model.refund.dto.RefundRejectDTO;
import com.kaipai.module.server.refund.service.RefundOrderService;
import com.kaipai.module.server.refund.service.RefundOperateLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台退款")
@RestController
@RequestMapping("/admin/refund")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundOrderService refundOrderService;
    private final RefundOperateLogService refundOperateLogService;

    @Operation(summary = "退款单列表")
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('page.refund.orders')")
    public R<PageResult<RefundOrderRespDTO>> orders(@Valid RefundOrderQueryDTO query) {
        return R.ok(refundOrderService.adminOrderList(query));
    }

    @Operation(summary = "退款单详情")
    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('page.refund.orders')")
    public R<RefundOrderDetailDTO> detail(@PathVariable Long id) {
        return R.ok(refundOrderService.adminOrderDetail(id));
    }

    @Operation(summary = "退款日志列表")
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('page.refund.logs')")
    public R<PageResult<RefundOperateLogItemDTO>> logs(@Valid RefundOperateLogQueryDTO query) {
        return R.ok(refundOperateLogService.adminLogList(query));
    }

    @Operation(summary = "审核通过退款")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('action.refund.approve')")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody RefundApproveDTO dto) {
        refundOrderService.approveRefund(id, dto);
        return R.ok();
    }

    @Operation(summary = "审核拒绝退款")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('action.refund.reject')")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody RefundRejectDTO dto) {
        refundOrderService.rejectRefund(id, dto);
        return R.ok();
    }
}
