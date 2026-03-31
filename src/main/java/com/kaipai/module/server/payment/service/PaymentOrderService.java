package com.kaipai.module.server.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderQueryDTO;
import com.kaipai.module.model.payment.entity.PaymentOrder;

public interface PaymentOrderService extends IService<PaymentOrder> {

    PageResult<AdminPaymentOrderListItemDTO> adminOrderList(AdminPaymentOrderQueryDTO query);

    AdminPaymentOrderDetailDTO adminOrderDetail(Long id);
}
