package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateRollbackDTO {

    private Long templateId;

    @NotBlank
    private String sourceVersion;

    private String publishNote;
}



