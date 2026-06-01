package com.kaipai.service.refund;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.refund.dto.RefundApproveDTO;
import com.kaipai.model.refund.dto.RefundOrderDetailDTO;
import com.kaipai.model.refund.dto.RefundOrderQueryDTO;
import com.kaipai.model.refund.dto.RefundOrderRespDTO;
import com.kaipai.model.refund.dto.RefundRejectDTO;
import com.kaipai.model.refund.entity.RefundOrder;

public interface RefundOrderService extends IService<RefundOrder> {

    PageResult<RefundOrderRespDTO> adminOrderList(RefundOrderQueryDTO query);

    RefundOrderDetailDTO adminOrderDetail(Long refundOrderId);

    void approveRefund(Long refundOrderId, RefundApproveDTO dto);

    void rejectRefund(Long refundOrderId, RefundRejectDTO dto);
}
