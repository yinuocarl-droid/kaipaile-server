package com.kaipai.module.model.adminauth.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminSessionInfoDTO {

    private Long adminUserId;
    private String account;
    private String userName;
    private String phone;
    private String email;
    private List<String> roleCodes;
    private List<String> menuPermissions;
    private List<String> pagePermissions;
    private List<String> actionPermissions;
}
