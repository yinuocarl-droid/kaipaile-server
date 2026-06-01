package com.kaipai.model.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Schema aligned with card_scene_template table defining scene templates.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("card_scene_template")
public class CardSceneTemplate extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long templateId;
    private String templateCode;
    private String templateSceneCode;
    private String templateName;
    private String description;
    private String layoutVariant;
    private String tier;
    private Integer requiredLevel;
    private Boolean unlockRequired;
    private String baseThemeJson;
    private String artifactPresetJson;
    private Integer status;
    private Integer sortNo;
}



