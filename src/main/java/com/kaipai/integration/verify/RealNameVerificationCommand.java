package com.kaipai.integration.verify;

public record RealNameVerificationCommand(
        Long userId,
        String realName,
        String idCardNo
) {
}
