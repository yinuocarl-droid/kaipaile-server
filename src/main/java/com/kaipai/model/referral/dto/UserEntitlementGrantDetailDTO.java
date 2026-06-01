package com.kaipai.model.referral.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserEntitlementGrantDetailDTO {

    private GrantInfo grantInfo;
    private SourceInfo sourceInfo;
    private RelatedOrderInfo relatedOrder;
    private RelatedPolicyInfo relatedPolicy;
    private OperatorLogSummary operatorLogSummary;

    @Data
    public static class GrantInfo {
        private Long grantId;
        private Long userId;
        private String userName;
        private String nickname;
        private String phone;
        private Integer userType;
        private Integer realAuthStatus;
        private Integer validInviteCount;
        private String grantType;
        private String grantCode;
        private Integer status;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String sourceType;
        private Long sourceRefId;
        private String remark;
        private Long createUserId;
        private String createUserName;
        private LocalDateTime createTime;
        private Long updateUserId;
        private String updateUserName;
        private LocalDateTime lastUpdate;
    }

    @Data
    public static class SourceInfo {
        private String sourceType;
        private Long sourceRefId;
        private String sourceTitle;
        private String sourceStatus;
        private String relatedBizType;
        private Long relatedBizId;
    }

    @Data
    public static class RelatedOrderInfo {
        private Long paymentOrderId;
        private String orderNo;
        private String bizType;
        private Long bizRefId;
        private BigDecimal amount;
        private Integer payStatus;
        private String payChannel;
        private LocalDateTime paidAt;
    }

    @Data
    public static class RelatedPolicyInfo {
        private Long policyId;
        private String policyName;
        private Integer enabled;
        private Integer autoGrantEnabled;
        private String updateUserName;
        private LocalDateTime lastUpdate;
    }

    @Data
    public static class OperatorLogSummary {
        private Long totalCount;
        private List<OperatorLogItem> recentLogs;
    }

    @Data
    public static class OperatorLogItem {
        private Long operationLogId;
        private Long adminUserId;
        private String adminUserName;
        private String operationCode;
        private Integer operationResult;
        private String beforeSnapshotJson;
        private String afterSnapshotJson;
        private String extraContextJson;
        private LocalDateTime createTime;
    }
}
