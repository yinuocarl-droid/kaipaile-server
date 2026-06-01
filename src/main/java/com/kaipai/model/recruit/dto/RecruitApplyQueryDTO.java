package com.kaipai.model.recruit.dto;

import lombok.Data;

@Data
public class RecruitApplyQueryDTO {

    private Integer page = 1;

    private Integer size = 20;

    /**
     * Frontend enum: 1 pending, 2 approved, 3 rejected, 4 cancelled.
     */
    private Integer status;

    private Long roleId;
}
