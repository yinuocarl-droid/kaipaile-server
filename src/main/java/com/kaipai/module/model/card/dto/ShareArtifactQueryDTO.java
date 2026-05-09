package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ShareArtifactQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long templateId;
    private String templateCode;
    private String templateSceneCode;
    private Integer status;
}



