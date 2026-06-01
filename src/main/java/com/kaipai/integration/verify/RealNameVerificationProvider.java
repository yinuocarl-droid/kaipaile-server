package com.kaipai.integration.verify;

public interface RealNameVerificationProvider {

    RealNameVerificationResult verify(RealNameVerificationCommand command);
}
