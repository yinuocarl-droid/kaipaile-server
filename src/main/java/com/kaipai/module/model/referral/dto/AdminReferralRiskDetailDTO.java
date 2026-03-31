package com.kaipai.module.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminReferralRiskDetailDTO {

    private RecordInfo recordInfo;
    private UserInfo inviterInfo;
    private UserInfo inviteeInfo;
    private RiskInfo riskInfo;
    private DeviceHitSummary deviceHitSummary;
    private SameHourHitSummary sameHourHitSummary;
    private List<HistoryLogItem> historyLogs;

    @Data
    public static class RecordInfo {
        private Long referralId;
        private String inviteCode;
        private Long inviteCodeId;
        private Long inviterUserId;
        private Long inviteeUserId;
        private Integer status;
        private Integer riskFlag;
        private String riskReason;
        private String registerDeviceFingerprint;
        private LocalDateTime registeredAt;
        private LocalDateTime validatedAt;
    }

    @Data
    public static class UserInfo {
        private Long userId;
        private String userName;
        private String phone;
        private String nickname;
        private Integer realAuthStatus;
        private Integer validInviteCount;
    }

    @Data
    public static class RiskInfo {
        private Integer currentStatus;
        private Integer riskFlag;
        private String riskReason;
    }

    @Data
    public static class DeviceHitSummary {
        private String deviceFingerprint;
        private Integer hitCount;
        private List<Long> relatedReferralIds;
    }

    @Data
    public static class SameHourHitSummary {
        private String inviteCode;
        private LocalDateTime hourStart;
        private LocalDateTime hourEnd;
        private Integer hitCount;
        private List<Long> relatedReferralIds;
    }

    @Data
    public static class HistoryLogItem {
        private Long operationLogId;
        private Long adminUserId;
        private String adminUserName;
        private String operationCode;
        private Integer operationResult;
        private String extraContextJson;
        private LocalDateTime createTime;
    }
}
