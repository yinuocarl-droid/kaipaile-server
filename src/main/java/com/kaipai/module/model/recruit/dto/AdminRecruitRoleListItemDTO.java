package com.kaipai.module.model.recruit.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminRecruitRoleListItemDTO {

    private Long roleId;

    private Long crewUserId;

    private Long crewProfileId;

    private Long projectId;

    private String projectTitle;

    private String crewName;

    private String roleName;

    private String gender;

    private Integer minAge;

    private Integer maxAge;

    private String requirement;

    private String fee;

    private String status;

    private String deadline;

    private Integer applyCount;

    private String location;

    private String contactName;

    private String contactPhone;

    private String publishTime;

    private String coverImage;

    private List<String> tags = new ArrayList<>();
}
