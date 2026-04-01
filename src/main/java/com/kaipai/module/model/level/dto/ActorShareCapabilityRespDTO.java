package com.kaipai.module.model.level.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActorShareCapabilityRespDTO {

    private Boolean canUseBasicCard;

    private Boolean canUsePersonalizedTheme;

    private Boolean canUseCustomMiniProgramCard;

    private Boolean canUseCustomPoster;

    private Boolean canUseCustomInviteCard;

    private Boolean canApplyFortuneTheme;

    private List<String> reasonCodes = new ArrayList<>();
}
