package com.kaipai.model.actor.card.dto;

import lombok.Data;

/**
 * 按步骤保存草稿请求 DTO
 * step 决定要保存哪个 JSON 字段，未传字段不覆盖
 */
@Data
public class ActorCardStepSaveReqDTO {

    /** 最近编辑步骤 1-7 */
    private Integer currentStep;

    /** 步骤1：主视觉 */
    private String style;
    private String backgroundImageUrl;
    private String sourceImageUrl;
    private String expandedImageUrl;

    /** 步骤2：个人资料快照 JSON */
    private String profileSnapshotJson;

    /** 步骤4：生活照片 URL 数组 JSON */
    private String photosJson;

    /** 步骤5：视频简历 URL */
    private String videoUrl;

    /** 步骤6：附件简历 URL */
    private String attachmentUrl;

    /** 步骤7：生成设置 JSON */
    private String settingsJson;

    /** 演员卡名称（可选，用户自定义时更新） */
    private String title;
}
