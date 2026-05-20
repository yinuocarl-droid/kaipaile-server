package com.kaipai.module.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Legacy multi-page profile-card page entity.
 * Current generation writes the primary result to ActorAiProfileCardTask.generatedImageUrl and share-card config;
 * this table mapping remains only for historical compatibility and cleanup of old page rows.
 */
@Deprecated(since = "Phase 5", forRemoval = false)
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_ai_profile_card_page")
public class ActorAiProfileCardPage extends BaseEntity {

    @TableId(value = "page_id", type = IdType.AUTO)
    private Long pageId;

    private String taskId;

    private Long shareCardId;

    private Integer pageNo;

    private String pageType;

    private String promptJson;

    private String promptText;

    private String negativePrompt;

    private String promptLocale;

    /**
     * Legacy continuity metadata for the retired multi-page album flow.
     * Current generation does not read or write continuity state on the main path.
     */
    @Deprecated(since = "Phase 5", forRemoval = false)
    private String continuityMode;

    @Deprecated(since = "Phase 5", forRemoval = false)
    private String continuityReferenceUrl;

    @Deprecated(since = "Phase 5", forRemoval = false)
    private String continuityReferenceSourcePageType;

    @Deprecated(since = "Phase 5", forRemoval = false)
    private Integer continuityReferenceSourcePageNo;

    @Deprecated(since = "Phase 5", forRemoval = false)
    private Double continuityBandRatio;

    @Deprecated(since = "Phase 5", forRemoval = false)
    private String continuityBandRect;

    @Deprecated(since = "Phase 5", forRemoval = false)
    private String continuityFailureReason;

    private String providerCode;

    private String modelCode;

    private String status;

    private String generatedImageUrl;

    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
