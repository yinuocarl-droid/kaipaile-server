package com.kaipai.module.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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

    private String continuityMode;

    private String continuityReferenceUrl;

    private String continuityReferenceSourcePageType;

    private Integer continuityReferenceSourcePageNo;

    private Double continuityBandRatio;

    private String continuityBandRect;

    private String continuityFailureReason;

    private String providerCode;

    private String modelCode;

    private String status;

    private String generatedImageUrl;

    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
