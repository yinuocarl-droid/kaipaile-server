package com.kaipai.module.server.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionQueryDTO;
import com.kaipai.module.model.payment.entity.PaymentTransaction;

public interface PaymentTransactionService extends IService<PaymentTransaction> {

    PageResult<AdminPaymentTransactionListItemDTO> adminTransactionList(AdminPaymentTransactionQueryDTO query);

    AdminPaymentTransactionDetailDTO adminTransactionDetail(Long id);
}
