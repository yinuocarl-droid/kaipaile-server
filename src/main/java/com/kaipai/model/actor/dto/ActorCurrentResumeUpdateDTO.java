package com.kaipai.model.actor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActorCurrentResumeUpdateDTO {
    @NotNull
    private Long assetId;
}
