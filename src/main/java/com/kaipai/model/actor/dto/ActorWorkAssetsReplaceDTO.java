package com.kaipai.model.actor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ActorWorkAssetsReplaceDTO {

    @Valid
    @NotNull
    private List<ActorAssetBindingDTO> bindings;
}
