package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplatePublishLogQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long templateId;
    private String publishVersion;
    private String actionType;
    private Long publishedBy;
    private LocalDateTime publishedAtFrom;
    private LocalDateTime publishedAtTo;
}



