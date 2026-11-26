package com.kaipai.model.actor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActorProfileIntroUpdateDTO {

    @NotNull
    private Integer expectedProfileVersion;

    @Size(max = 2000)
    private String intro;
}
