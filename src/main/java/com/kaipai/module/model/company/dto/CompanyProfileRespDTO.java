package com.kaipai.module.model.company.dto;

import lombok.Data;

@Data
public class CompanyProfileRespDTO {

    private Long userId;

    private String avatar;

    private String companyName;

    private String contactName;

    private String contactPhone;

    private String remark;

    private String location;

    private String companyType;

    private String teamScale;

    private String focusDirection;

    private String representativeWorks;

    private String cooperationNeed;

    private String officeAddress;
}
