package com.kaipai.model.recruit.dto;

import com.kaipai.model.crew.dto.CrewProfileRespDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleRespDTO {

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

    private ProjectRespDTO project;

    private CrewProfileRespDTO crew;
}
