package com.kaipai.model.crew.dto;

import com.kaipai.model.recruit.dto.ProjectRespDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CrewProfileExtrasDTO {

    private String crewType;

    private String teamScale;

    private String focusDirection;

    private String representativeWorks;

    private String cooperationNeed;

    private String officeAddress;

    private List<ProjectRespDTO> projects = new ArrayList<>();
}
