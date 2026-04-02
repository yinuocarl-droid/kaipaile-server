package com.kaipai.module.model.recruit.dto;

import lombok.Data;

@Data
public class RoleSaveDTO {

    private Long projectId;

    private String roleName;

    private String gender;

    private Integer minAge;

    private Integer maxAge;

    private String requirement;

    private String fee;

    private String deadline;

    private String coverImage;
}
