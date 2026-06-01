package com.kaipai.model.user.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserAdminDetailDTO {

    private UserInfo userInfo;
    private ActorProfileSummary actorProfileSummary;
    private VerifySummary verifySummary;
    private ReferralSummary referralSummary;
    private UserAdminEntitlementSummaryDTO entitlementSummary;
    private PaymentSummary paymentSummary;
    private RefundSummary refundSummary;
    private List<RecentOperationLogItem> recentOperationLogs;

    @Data
    public static class UserInfo {
        private Long userId;
        private String userNo;
        private String account;
        private String phone;
        private String email;
        private String userName;
        private String avatarUrl;
        private Integer userType;
        private String role;
        private Integer registerSource;
        private Integer realAuthStatus;
        private Long invitedByUserId;
        private Integer validInviteCount;
        private String registerDeviceFingerprint;
        private Integer status;
        private String remark;
        private LocalDateTime registeredAt;
        private LocalDateTime lastActiveAt;
        private String lastLoginIp;
    }

    @Data
    public static class ActorProfileSummary {
        private Long actorProfileId;
        private String actorNo;
        private String nickName;
        private String realName;
        private Integer gender;
        private LocalDate birthday;
        private Integer age;
        private String locationProvince;
        private String locationCity;
        private String avatarUrl;
        private Boolean certified;
        private Boolean openApply;
        private Integer profileStatus;
    }

    @Data
    public static class VerifySummary {
        private Integer realAuthStatus;
        private Long latestVerificationId;
        private Integer latestVerificationStatus;
        private String latestRealName;
        private String latestRejectReason;
        private LocalDateTime latestSubmittedAt;
        private LocalDateTime latestReviewedAt;
    }

    @Data
    public static class ReferralSummary {
        private String inviteCode;
        private Integer inviteCodeStatus;
        private Long invitedByUserId;
        private Integer referralStatus;
        private Long referralId;
        private Integer riskFlag;
        private String riskReason;
        private Integer validInviteCount;
        private Integer totalInviteCount;
        private Integer pendingInviteCount;
        private Integer invalidInviteCount;
        private LocalDateTime lastInvitedAt;
    }

    @Data
    public static class PaymentSummary {
        private Integer totalOrderCount;
        private Integer paidOrderCount;
        private BigDecimal totalPaidAmount;
        private List<PaymentOrderSummaryItem> recentOrders;
    }

    @Data
    public static class PaymentOrderSummaryItem {
        private Long paymentOrderId;
        private String orderNo;
        private BigDecimal amount;
        private Integer payStatus;
        private String payChannel;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
    }

    @Data
    public static class RefundSummary {
        private Integer totalRefundCount;
        private Integer pendingRefundCount;
        private Integer processingRefundCount;
        private Integer successRefundCount;
        private BigDecimal totalRefundAmount;
        private List<RefundOrderSummaryItem> recentRefunds;
    }

    @Data
    public static class RefundOrderSummaryItem {
        private Long refundOrderId;
        private String refundNo;
        private BigDecimal refundAmount;
        private Integer auditStatus;
        private Integer refundStatus;
        private LocalDateTime appliedAt;
        private LocalDateTime refundedAt;
    }

    @Data
    public static class RecentOperationLogItem {
        private Long operationLogId;
        private Long adminUserId;
        private String adminUserName;
        private String moduleCode;
        private String operationCode;
        private String targetType;
        private Long targetId;
        private Integer operationResult;
        private LocalDateTime createTime;
    }
}
