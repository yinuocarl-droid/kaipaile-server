package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ThemeTokenQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private String sceneKey;
    private Integer status;
    private Long templateId;
    private String templateCode;
}
