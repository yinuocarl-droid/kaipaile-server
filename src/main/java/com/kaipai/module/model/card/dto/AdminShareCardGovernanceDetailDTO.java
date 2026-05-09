package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminShareCardGovernanceDetailDTO {

    private CardInfo cardInfo;

    private UserInfo ownerInfo;

    private BindingInfo bindingInfo;

    private StatsInfo statsInfo;

    @Data
    public static class CardInfo {
        private Long shareCardId;
        private String templateSceneCode;
        private String templateName;
        private String shareStatus;
        private Boolean defaultCard;
        private Long profileUserId;
        private Long templateId;
        private Long configId;
        private LocalDateTime createTime;
        private LocalDateTime lastUpdate;
    }

    @Data
    public static class UserInfo {
        private Long userId;
        private String userName;
        private String nickName;
        private String displayName;
        private String phone;
        private String avatarUrl;
        private Integer realAuthStatus;
        private Integer validInviteCount;
    }

    @Data
    public static class BindingInfo {
        private Long configId;
        private String configTemplateSceneCode;
        private Boolean bindingConsistent;
        private List<String> issues;
    }

    @Data
    public static class StatsInfo {
        private Long historyCount;
        private Long totalContactRequestCount;
        private Long pendingContactRequestCount;
        private Long approvedContactRequestCount;
        private Long rejectedContactRequestCount;
        private LocalDateTime latestViewedAt;
        private LocalDateTime latestRequestedAt;
    }
}



