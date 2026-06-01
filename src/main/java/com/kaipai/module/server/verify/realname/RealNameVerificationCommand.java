package com.kaipai.module.server.verify.realname;

public record RealNameVerificationCommand(
        Long userId,
        String realName,
        String idCardNo
) {
}
