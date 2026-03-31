package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplatePublishDTO {

    @NotNull
    private Long templateId;

    private String publishVersion;
    private String publishNote;
}
