package com.kaipai.service.payment;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.payment.dto.AdminPaymentOrderDetailDTO;
import com.kaipai.model.payment.dto.AdminPaymentOrderListItemDTO;
import com.kaipai.model.payment.dto.AdminPaymentOrderQueryDTO;
import com.kaipai.model.payment.entity.PaymentOrder;

public interface PaymentOrderService extends IService<PaymentOrder> {

    PageResult<AdminPaymentOrderListItemDTO> adminOrderList(AdminPaymentOrderQueryDTO query);

    AdminPaymentOrderDetailDTO adminOrderDetail(Long id);
}
