package com.kaipai.model.system.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminUserQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private String account;
    private String userName;
    private String phone;
    private Integer status;
    private String roleCode;
}


