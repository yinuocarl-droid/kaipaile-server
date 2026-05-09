package com.kaipai.module.model.system.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AdminUserDetailRespDTO {

    private Long adminUserId;
    private String account;
    private String userName;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String createUserName;
    private LocalDateTime createTime;
    private String updateUserName;
    private LocalDateTime lastUpdate;
    private List<AdminRoleBriefDTO> roles;
}


