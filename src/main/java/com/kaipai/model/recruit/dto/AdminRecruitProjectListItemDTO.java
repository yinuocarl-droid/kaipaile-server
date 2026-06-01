package com.kaipai.model.recruit.dto;

import lombok.Data;

@Data
public class AdminRecruitProjectListItemDTO {

    private Long projectId;

    private Long crewUserId;

    private Long crewProfileId;

    private String crewName;

    private String contactName;

    private String contactPhone;

    private String title;

    private String description;

    private String location;

    private Integer status;

    private String type;

    private String shootingDate;

    private Integer roleCount;

    private String coverImage;

    private String sourceUpdatedAt;

    private String sourceCreatedAt;
}
