package com.kaipai.module.model.company.dto;

import com.kaipai.module.model.recruit.dto.ProjectRespDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyProfileExtrasDTO {

    private String companyType;

    private String teamScale;

    private String focusDirection;

    private String representativeWorks;

    private String cooperationNeed;

    private String officeAddress;

    private List<ProjectRespDTO> projects = new ArrayList<>();
}
