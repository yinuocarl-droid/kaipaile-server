package com.kaipai.module.model.recruit.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RecruitRoleRespDTO {

    private Long id;

    private Long projectId;

    private String roleName;

    private String gender;

    private Integer minAge;

    private Integer maxAge;

    private String requirement;

    private String fee;

    private String deadline;

    private String status;

    private List<String> tags = new ArrayList<>();

    private String publishTime;

    private String coverImage;

    private ProjectDTO project;

    private CrewDTO crew;

    @Data
    public static class ProjectDTO {

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

    @Data
    public static class CrewDTO {

        private Long userId;

        private String avatar;

        private String crewName;

        private String contactName;

        private String contactPhone;

        private String remark;

        private String location;

        private String crewType;

        private String teamScale;

        private String focusDirection;

        private String representativeWorks;

        private String cooperationNeed;

        private String officeAddress;
    }
}
