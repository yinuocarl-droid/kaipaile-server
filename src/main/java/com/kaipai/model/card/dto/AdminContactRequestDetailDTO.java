package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminContactRequestDetailDTO {

    private RequestInfo requestInfo;
    private CardInfo cardInfo;
    private UserInfo ownerInfo;
    private UserInfo viewerInfo;

    @Data
    public static class RequestInfo {
        private Long requestId;
        private String status;
        private String templateName;
        private String applicantNote;
        private String decisionNote;
        private LocalDateTime requestedAt;
        private LocalDateTime decidedAt;
    }

    @Data
    public static class CardInfo {
        private Long shareCardId;
        private Long profileUserId;
        private String templateSceneCode;
        private String shareStatus;
        private Boolean defaultCard;
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
}



