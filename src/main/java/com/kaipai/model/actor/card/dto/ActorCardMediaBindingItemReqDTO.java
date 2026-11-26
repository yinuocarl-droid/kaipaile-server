package com.kaipai.model.actor.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActorCardMediaBindingItemReqDTO {

    @NotNull
    private Long assetId;

    @NotNull
    private Integer sortOrder;
}
