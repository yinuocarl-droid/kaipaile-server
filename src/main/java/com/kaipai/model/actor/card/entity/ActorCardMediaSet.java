package com.kaipai.model.actor.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_card_media_set")
public class ActorCardMediaSet extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long setId;

    private Long cardId;

    private Long cardWorkId;

    private String usageCode;
}
