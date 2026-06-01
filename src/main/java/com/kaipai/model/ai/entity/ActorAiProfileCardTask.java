package com.kaipai.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_ai_profile_card_task")
public class ActorAiProfileCardTask extends BaseEntity {

    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    private Long userId;

    private Long actorProfileId;

    private Long shareCardId;

    private String templateSceneCode;

    private String styleCode;

    private String sourceImageUrl;

    private String providerCode;

    private String modelCode;

    private String generationMode;

    private String promptJson;

    private String promptText;

    private String negativePrompt;

    private String status;

    private String generatedImageUrl;

    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
