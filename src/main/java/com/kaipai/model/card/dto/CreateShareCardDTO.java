package com.kaipai.model.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateShareCardDTO {

    @NotBlank
    private String templateSceneCode;
}



