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

    /**
     * 步骤6：附件简历 URL。
     * 已停写：附件在私有桶，读取只能靠 10 分钟预签名 URL，持久化 URL 必然过期失效。
     * 新逻辑走 {@link #attachment}，此字段仅为兼容历史请求体保留，服务端不再采纳其新值。
     */
    @Deprecated
    private String attachmentUrl;

    /**
     * 步骤6：附件简历绑定。三态语义，解决「null = 不变」与「清空」用同一标量字段无法区分的问题：
     * <ul>
     *   <li>不传 {@code attachment} 键 → 不动该字段（跳过 / 下一步走这条）</li>
     *   <li>{@code attachment: { assetId: 123 }} → 绑定并验权</li>
     *   <li>{@code attachment: { assetId: null }} → 显式清空（仅「删除」按钮触发）</li>
     * </ul>
     * 用嵌套对象而非 {@code clearAttachment} 标志位，是为了让清空与赋值共用一个字段，
     * 不存在「同时传 assetId 和 clear=true」这种自相矛盾的入参。
     */
    private AttachmentBinding attachment;

    /** 步骤7：生成设置 JSON */
    private String settingsJson;

    /** 演员卡名称（可选，用户自定义时更新） */
    private String title;

    @Data
    public static class AttachmentBinding {
        /** null 表示显式清空；非 null 表示绑定该素材，服务端会校验归属与就绪状态 */
        private Long assetId;
    }
}
