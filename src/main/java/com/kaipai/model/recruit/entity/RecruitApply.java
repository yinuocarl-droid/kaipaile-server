package com.kaipai.model.recruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruit_apply")
public class RecruitApply extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long recruitApplyId;

    private Long recruitPostId;

    private Long actorUserId;

    private Long actorProfileId;

    private String applyMessage;

    /** 投递状态: 1待处理, 2已查看, 3邀约面试, 4已录用, 5已拒绝 */
    private Integer applyStatus;

    private String extendedField;
}
