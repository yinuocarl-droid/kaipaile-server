package com.kaipai.module.model.adminauth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginRespDTO {

    private String accessToken;
    private AdminSessionInfoDTO adminUserInfo;
}
