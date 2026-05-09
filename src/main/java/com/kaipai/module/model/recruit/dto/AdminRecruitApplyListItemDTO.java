package com.kaipai.module.model.recruit.dto;

import lombok.Data;

@Data
public class AdminRecruitApplyListItemDTO {

    private Long applyId;

    private Long roleId;

    private Long actorUserId;

    private Long crewUserId;

    private Long projectId;

    private String projectTitle;

    private String crewName;

    private String roleName;

    private String roleStatus;

    private String actorName;

    private String actorPhone;

    private String actorAvatar;

    private Integer status;

    private String remark;

    private String applyTime;
}
