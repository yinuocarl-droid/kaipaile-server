package com.kaipai.model.actor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActorAssetBindingDTO {
    @NotNull private Long assetId;
    @NotBlank private String usageCode;
    private Integer sortNo = 0;
}
