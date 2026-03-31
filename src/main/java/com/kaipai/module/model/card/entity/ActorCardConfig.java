package com.kaipai.module.model.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_card_config")
public class ActorCardConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long configId;
    private Long userId;
    private Long actorProfileId;
    private String sceneKey;
    private Long templateId;
    private String layoutVariant;
    private String primaryColor;
    private String accentColor;
    private String backgroundColor;
    private String highlightedExperienceIds;
    private String highlightedPhotoUrls;
    private String tagOrderJson;
}
