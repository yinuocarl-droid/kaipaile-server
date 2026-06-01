package com.kaipai.model.crew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intermediary_actor")
public class IntermediaryActor extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long intermediaryUserId;

    private Long intermediaryProfileId;

    private Long actorUserId;

    private Long actorProfileId;

    /** 授权状态: 1待演员确认, 2已授权, 3已解除 */
    private Integer authStatus;

    private LocalDateTime authTime;

    private LocalDateTime releaseTime;

    private String remark;

    private String extendedField;
}
