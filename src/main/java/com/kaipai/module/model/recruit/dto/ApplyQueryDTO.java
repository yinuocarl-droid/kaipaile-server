package com.kaipai.module.model.recruit.dto;

import lombok.Data;

@Data
public class ApplyQueryDTO {

    private Integer page;

    private Integer size;

    private Integer status;

    private Long roleId;
}
