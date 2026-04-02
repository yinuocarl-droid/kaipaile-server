package com.kaipai.module.model.recruit.dto;

import lombok.Data;

@Data
public class ProjectQueryDTO {

    private Integer page;

    private Integer size;

    private Integer status;

    private String location;

    private String keyword;
}
