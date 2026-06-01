package com.kaipai.module.server.verify.realname;

public interface RealNameVerificationProvider {

    RealNameVerificationResult verify(RealNameVerificationCommand command);
}
