package com.kaipai.model.adminauth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginReqDTO {

    @NotBlank
    private String account;

    @NotBlank
    private String password;
}
