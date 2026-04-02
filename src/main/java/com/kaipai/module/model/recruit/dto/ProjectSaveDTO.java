package com.kaipai.module.model.recruit.dto;

import lombok.Data;

@Data
public class ProjectSaveDTO {

    private Long companyId;

    private String title;

    private String description;

    private String location;

    private Integer status;

    private String type;

    private String shootingDate;

    private String coverImage;
}
