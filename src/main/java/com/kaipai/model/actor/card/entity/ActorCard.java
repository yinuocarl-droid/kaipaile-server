package com.kaipai.model.actor.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_card")
public class ActorCard extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 状态: draft=草稿, published=已发布 */
    private String status;

    /** 演员卡名称，如「都市演员卡」 */
    private String title;

    /** 风格: classic|urban|ancient|fresh */
    private String style;

    /** 最近编辑步骤 1-7 */
    private Integer currentStep;

    /** 各步骤完成状态 JSON */
    private String stepStatusJson;

    /** 已选背景图 URL */
    private String backgroundImageUrl;

    /** 原始首图 URL（用户上传） */
    private String sourceImageUrl;

    /** AI 扩图后首图 URL */
    private String expandedImageUrl;

    /** 个人资料快照 JSON（步骤 2） */
    private String profileSnapshotJson;

    /** 生活照片 URL 数组 JSON（步骤 4） */
    private String photosJson;

    /** 视频简历 URL（步骤 5） */
    private String videoUrl;

    /** 附件简历 URL（步骤 6） */
    private String attachmentUrl;

    /** 生成设置 JSON（步骤 7） */
    private String settingsJson;

    /** AI 生成长页预览 URL */
    private String generatedPreviewUrl;

    /** 已发布版本号，每次重新发布 +1 */
    private Integer publishedVersion;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;
}
