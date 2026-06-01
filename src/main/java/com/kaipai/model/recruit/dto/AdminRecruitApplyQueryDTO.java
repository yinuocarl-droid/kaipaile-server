package com.kaipai.model.recruit.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminRecruitApplyQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private Long applyId;

    private Long roleId;

    private Long actorUserId;

    private Long crewUserId;

    private Integer status;

    private String keyword;
}
