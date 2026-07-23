package com.kaipai.model.actor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActorProfileMineUpdateDTO {

    @NotNull
    private Integer expectedProfileVersion;

    @NotNull
    private Long avatarAssetId;

    @NotNull
    @Valid
    private ActorProfileCoreUpdateDTO core;

    @NotNull
    @Valid
    private ActorProfileCareerUpdateDTO career;

    @Size(max = 2000)
    private String intro;
}
