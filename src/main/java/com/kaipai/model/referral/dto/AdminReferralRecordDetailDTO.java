package com.kaipai.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminReferralRecordDetailDTO {

    private RecordInfo recordInfo;
    private UserInfo inviterInfo;
    private UserInfo inviteeInfo;
    private RiskInfo riskInfo;

    @Data
    public static class RecordInfo {
        private Long referralId;
        private String inviteCode;
        private Long inviteCodeId;
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
        private Integer status;
        private Integer riskFlag;
        private String riskReason;
        private String registerDeviceFingerprint;
        private Integer sameDeviceHitCount;
        private List<String> relatedGrantCodes;
    }
}
