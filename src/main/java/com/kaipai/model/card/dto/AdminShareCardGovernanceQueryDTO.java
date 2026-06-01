package com.kaipai.model.card.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminShareCardGovernanceQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long shareCardId;

    private Long holderUserId;

    private String templateSceneCode;

    private String shareStatus;

    private Boolean defaultCard;
}



