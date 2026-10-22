package com.kaipai.model.actor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActorProfileMineUpdateDTO {

    @NotNull
    private Integer expectedProfileVersion;

    @NotNull
    private Long avatarAssetId;

    /** Optional for old clients; account nickname is independent of publicName. */
    @Size(min = 1, max = 64)
    @Pattern(regexp = "(?s).*\\S.*", message = "昵称不能为空")
    private String nickname;

    @NotNull
    @Valid
    private ActorProfileCoreUpdateDTO core;

    @NotNull
    @Valid
    private ActorProfileCareerUpdateDTO career;

    @Size(max = 2000)
    private String intro;
}
