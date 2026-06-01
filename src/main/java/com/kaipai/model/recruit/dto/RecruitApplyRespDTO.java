package com.kaipai.model.recruit.dto;

import lombok.Data;

@Data
public class RecruitApplyRespDTO {

    private Long id;

    private Long roleId;

    private Long profileUserId;

    /**
     * Frontend enum: 1 pending, 2 approved, 3 rejected, 4 cancelled.
     */
    private Integer status;

    private String remark;

    private String applyTime;

    private String actorName;

    private String actorAvatar;

    private String actorPhone;

    private String roleName;

    private String projectName;

    private RecruitRoleRespDTO role;
}
