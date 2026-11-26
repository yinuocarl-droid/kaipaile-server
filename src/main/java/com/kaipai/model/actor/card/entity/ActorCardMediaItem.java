package com.kaipai.model.actor.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_card_media_item")
public class ActorCardMediaItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long itemId;

    private Long setId;

    private Long assetId;

    private String legacyUrl;

    private Integer sortOrder;

    @TableField(exist = false)
    private Long activeAssetId;
}
