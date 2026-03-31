package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ThemeTokenUpdateDTO {

    @NotBlank
    private String baseThemeJson;
}
