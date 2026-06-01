package com.kaipai.model.recruit.dto;

import com.kaipai.model.actor.dto.ActorProfileDTO;
import lombok.Data;

@Data
public class ApplyRespDTO {

    private Long id;

    private Long roleId;

    private Long profileUserId;

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
