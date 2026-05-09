package com.kaipai.module.model.capability.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminCapabilityAccountDetailDTO {

    private Long userId;
    private String nickname;
    private String phone;
    private CurrentAccount currentAccount;
    private List<PaymentOrderSummary> relatedPaymentOrders;
    private List<GrantSummary> relatedGrants;
    private List<CapabilityChangeLogItemDTO> changeLogs;

    @Data
    public static class CurrentAccount {
        private Long capabilityId;
        private Integer tier;
        private Integer status;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String sourceType;
        private Long sourceRefId;
    }

    @Data
    public static class PaymentOrderSummary {
        private Long paymentOrderId;
        private String orderNo;
        private BigDecimal amount;
        private Integer payStatus;
        private String payChannel;
        private LocalDateTime createTime;
        private LocalDateTime paidAt;
    }

    @Data
    public static class GrantSummary {
        private Long grantId;
        private String grantType;
        private String grantCode;
        private Integer status;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String sourceType;
        private Long sourceRefId;
        private String remark;
    }
}
