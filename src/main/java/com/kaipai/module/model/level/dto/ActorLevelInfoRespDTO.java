package com.kaipai.module.model.level.dto;

import lombok.Data;

@Data
public class ActorLevelInfoRespDTO {

    private Integer level;

    private Integer inviteCount;

    private Integer nextLevelRequirement;

    private Boolean isCertified;

    private Integer profileCompletion;
}
