package com.kaipai.model.actor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

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

    /** Missing/null preserves the existing value; empty string explicitly clears. */
    private String heroBadgeText;

    /** JSON numbers/booleans must not silently coerce into actor-authored text. */
    @JsonSetter("heroBadgeText")
    public void readHeroBadgeText(JsonNode value) {
        if (value == null || value.isNull()) { heroBadgeText = null; return; }
        if (!value.isTextual()) throw new IllegalArgumentException("heroBadgeText must be a string or null");
        heroBadgeText = value.textValue();
    }
}
