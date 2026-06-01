package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminShareCardGovernanceItemDTO {

    private Long shareCardId;

    private Long holderUserId;

    private String ownerName;

    private String ownerPhone;

    private String templateSceneCode;

    private String templateName;

    private String shareStatus;

    private Boolean defaultCard;

    private Long profileUserId;

    private Long templateId;

    private Long configId;

    private Boolean bindingConsistent;

    private Integer issueCount;

    private Long historyCount;

    private Long totalContactRequestCount;

    private Long pendingContactRequestCount;

    private Long approvedContactRequestCount;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}



