package com.kaipai.service.payment;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.payment.dto.AdminPaymentTransactionDetailDTO;
import com.kaipai.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.model.payment.dto.AdminPaymentTransactionQueryDTO;
import com.kaipai.model.payment.entity.PaymentTransaction;

public interface PaymentTransactionService extends IService<PaymentTransaction> {

    PageResult<AdminPaymentTransactionListItemDTO> adminTransactionList(AdminPaymentTransactionQueryDTO query);

    AdminPaymentTransactionDetailDTO adminTransactionDetail(Long id);
}
