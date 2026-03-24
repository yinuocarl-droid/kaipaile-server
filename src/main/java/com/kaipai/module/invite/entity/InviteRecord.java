package com.kaipai.module.invite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("invite_record")
public class InviteRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long inviteId;

    private String inviteNo;

    private Long invitorUserId;

    /** 邀约方类型: 1剧组, 2中介 */
    private Integer invitorType;

    private Long companyProfileId;

    private Long actorUserId;

    private Long actorProfileId;

    private String dramaName;

    private String roleName;

    private String roleDesc;

    private LocalDateTime shootStartTime;

    private LocalDateTime shootEndTime;

    private String shootProvince;

    private String shootCity;

    private String shootAddress;

    private String salary;

    private String inviteMessage;

    /** 邀约状态: 1待确认, 2已接受, 3已拒绝, 4已撤销 */
    private Integer inviteStatus;

    private String rejectReason;

    private String extendedField;
}
