package com.kaipai.model.recruit.dto;

import lombok.Data;

@Data
public class ProjectRespDTO {

    private Long id;

    private Long crewId;

    private String title;

    private String description;

    private String location;

    private Integer status;

    private String type;

    private String shootingDate;

    private Integer roleCount;

    private String coverImage;
}
