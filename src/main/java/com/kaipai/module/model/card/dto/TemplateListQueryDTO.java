package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TemplateListQueryDTO {

    private String sceneKey;
    private Integer status;
    private String tier;

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;
}
