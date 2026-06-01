package com.kaipai.model.verify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IdentityVerificationSubmitReqDTO {

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "\\d{17}[\\dXx]", message = "身份证号格式不正确")
    private String idCardNo;
}
