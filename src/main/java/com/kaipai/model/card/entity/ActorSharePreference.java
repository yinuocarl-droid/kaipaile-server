package com.kaipai.model.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_share_preference")
public class ActorSharePreference extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long preferenceId;
    private Long shareCardId;
    private String preferredArtifact;
}



