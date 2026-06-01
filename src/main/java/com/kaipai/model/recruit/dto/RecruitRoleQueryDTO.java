package com.kaipai.model.recruit.dto;

import lombok.Data;

@Data
public class RecruitRoleQueryDTO {

    private Integer page = 1;

    private Integer size = 20;

    private String gender;

    private Integer minAge;

    private Integer maxAge;

    private String keyword;
}
