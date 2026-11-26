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
    private String profileSnapshotJson;
    private String photosJson;
    private String videoUrl;

    /**
     * 步骤6：历史附件 URL。已停写，仅回填供前端识别历史草稿并提供删除入口。
     * 新数据一律看 {@link #attachmentAssetId}。
     */
    @Deprecated
    private String attachmentUrl;

    /** 步骤6：附件简历素材 id */
    private Long attachmentAssetId;

    /** 附件文件名，服务端派生只读。前端渲染文件卡不必再发一次请求 */
    private String attachmentName;

    /** 附件页数，服务端派生只读 */
    private Integer attachmentPageCount;

    /** 附件处理状态 processing|ready|failed，服务端派生只读 */
    private String attachmentStatus;

    private String settingsJson;
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
