package com.kaipai.model.actor.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Stable inputs and the currently applicable AI main-visual result for one actor card. */
@Data
@TableName("actor_card_visual_input")
public class ActorCardVisualInput {

    @TableId(value = "card_id", type = IdType.INPUT)
    private Long cardId;
    private Long userId;
    private String generationMode;
    private String personSourceType;
    private Long personAssetId;
    private String personImageUrl;
    private Long backgroundId;
    private String backgroundImageUrl;
    private Long revision;
    private String inputFingerprint;
    private String generationConfigVersion;
    private String generatedImageUrl;
    private String generatedFingerprint;
    private String latestTaskId;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdate;
}
