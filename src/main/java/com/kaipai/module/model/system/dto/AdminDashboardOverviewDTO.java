package com.kaipai.module.model.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminDashboardOverviewDTO {

    private Long verifyPendingCount;
    private Long referralRiskPendingCount;
    private Long refundPendingCount;
    private Long todayPaymentOrderCount;
    private Long activeShareCardCount;
    private Long activeShareOwnerCount;
    private Long shareViewCount;
    private Long uniqueViewerCount;
    private Long approvedContactRequestCount;
    private Long pendingContactRequestCount;
    private Long convertedViewerCount;
    private Long classicSceneViewCount;
    private Long urbanSceneViewCount;
    private Long costumeSceneViewCount;
    private List<RecentItem> recentItems;

    @Data
    public static class RecentItem {
        private String bizLine;
        private String itemType;
        private Long itemId;
        private Long userId;
        private String referenceNo;
        private String title;
        private Integer status;
        private LocalDateTime occurredAt;
    }
}


