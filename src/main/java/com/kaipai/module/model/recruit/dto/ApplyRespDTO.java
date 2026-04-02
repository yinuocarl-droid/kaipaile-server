package com.kaipai.module.model.recruit.dto;

import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import lombok.Data;

@Data
public class ApplyRespDTO {

    private Long id;

    private Long roleId;

    private Long actorId;

    private Integer status;

    private String remark;

    private String applyTime;

    private String actorName;

    private String actorAvatar;

    private String actorPhone;

    private String roleName;

    private String projectName;

    private ActorProfileDTO actorProfile;

    private RoleRespDTO role;
}
