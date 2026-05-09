package com.kaipai.module.model.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_share_card")
public class UserShareCard extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long shareCardId;

    private Long userId;

    private Long actorProfileId;

    private Long templateId;

    private String shareStatus;

    private Boolean defaultCard;
}



