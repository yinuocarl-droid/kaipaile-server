package com.kaipai.model.recruit.dto;

import lombok.Data;

@Data
public class RoleQueryDTO {

    private Integer page;

    private Integer size;

    private Long projectId;

    private String gender;

    private Integer minAge;

    private Integer maxAge;

    private String keyword;
}
