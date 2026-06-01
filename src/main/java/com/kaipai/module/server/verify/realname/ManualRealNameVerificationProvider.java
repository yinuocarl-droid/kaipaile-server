package com.kaipai.module.server.verify.realname;

import org.springframework.stereotype.Component;

@Component
public class ManualRealNameVerificationProvider {

    public RealNameVerificationResult verify(RealNameVerificationCommand command) {
        return RealNameVerificationResult.manual("人工审核模式");
    }
}
