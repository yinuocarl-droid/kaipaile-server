package com.kaipai.model.recruit.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminRecruitProjectQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private Long projectId;

    private Long crewUserId;

    private Integer status;

    private String keyword;

    private String location;
}
