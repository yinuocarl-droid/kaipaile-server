package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.util.List;

/**
 * 已发布演员卡公开视图（观看者分享落地页数据）。
 * Requirements: 00-215 § 3.3 / 00-218
 * 注意：settings 顺序字段前端实际存的是 {@code order}（step-settings 写 settingsJson），
 * 与 00-215 草案中的 moduleOrder 命名不同，以实际数据字段 {@code order} 为准。
 */
@Data
public class ActorCardPublicRespDTO {

    private Long id;

    private String title;

    /** 风格: classic|urban|ancient|fresh */
    private String style;

    /** 主视觉：扩图 > 原图 > 生成预览 */
    private String previewImageUrl;

    private ProfileVO profile;

    private List<WorkVO> works;

    private List<String> photos;

    private VideoVO video;

    private AttachmentVO attachment;

    private SettingsVO settings;

    @Data
    public static class ProfileVO {
        private String name;
        private String height;
        private String city;
        private String school;
        private String contact;
        private String introduction;
    }

    @Data
    public static class WorkVO {
        private Long id;
        private String title;
        private String role;
        private String workType;
        private List<String> stills;
    }

    @Data
    public static class VideoVO {
        /** 视频 URL（当前 v2 向导存本地临时路径时为空，资源上传链路接通后填充） */
        private String url;
    }

    @Data
    public static class AttachmentVO {
        private Long assetId;
        private String filename;
    }

    @Data
    public static class SettingsVO {
        private Boolean showContact = true;
        private Boolean showVideo = true;
        private Boolean showAttachment = true;
        /** 模块展示顺序，默认 works -> photos -> video -> attachment */
        private List<String> order = List.of("works", "photos", "video", "attachment");
    }
}
