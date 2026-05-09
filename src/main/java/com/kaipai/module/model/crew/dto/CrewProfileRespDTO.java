package com.kaipai.module.model.crew.dto;

import lombok.Data;

@Data
public class CrewProfileRespDTO {

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
