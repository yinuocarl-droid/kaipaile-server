package com.kaipai.model.actor.card.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ActorCardMediaBindingReqDTO {

    @NotBlank
    private String usage;

    private Long workId;

    @Valid
    @NotNull
    private List<ActorCardMediaBindingItemReqDTO> items;
}
