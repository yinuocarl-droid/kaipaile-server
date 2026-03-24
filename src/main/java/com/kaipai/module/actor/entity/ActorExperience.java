package com.kaipai.module.actor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_experience")
public class ActorExperience extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long experienceId;

    private Long userId;

    private Long actorProfileId;

    private String dramaName;

    private String roleName;

    /** 作品类型: 1短剧, 2电影, 3电视剧, 4广告, 5其他 */
    private Integer dramaType;

    private Integer shootYear;

    private Integer shootMonth;

    private String platform;

    private String roleDesc;

    private Integer sortNo;

    private String extendedField;
}
