package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActorCardRespDTO {

    private Long id;
    private String status;
    private String title;
    private String style;
    private Integer currentStep;
    private Integer completionPercentage;
    private String backgroundImageUrl;
    private String sourceImageUrl;
    private String expandedImageUrl;
    private String generatedPreviewUrl;
    private Integer publishedVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdate;

    /** 各步骤完成状态，Hub 页用 */
    private List<StepStatus> stepStatuses;

    @Data
    public static class StepStatus {
        private Integer step;
        private String statusCode;  // done|pending|empty
        private String statusLabel; // "已完成" / "待确认" / "未添加" / "N张"
    }
}
