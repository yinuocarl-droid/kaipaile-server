package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateRollbackDTO {

    @NotNull
    private Long templateId;

    @NotNull
    private String sourceVersion;

    private String publishNote;
}
