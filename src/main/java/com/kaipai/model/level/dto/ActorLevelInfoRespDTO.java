package com.kaipai.model.level.dto;

import lombok.Data;

@Data
public class ActorLevelInfoRespDTO {

    private Integer level;

    private Integer inviteCount;

    private Integer nextLevelRequirement;

    private Boolean isCertified;

    private Integer profileCompletion;

    private String capabilityTier;

    private ActorLevelCapabilityRespDTO levelCapability;

    private ActorShareCapabilityRespDTO shareCapability;
}
