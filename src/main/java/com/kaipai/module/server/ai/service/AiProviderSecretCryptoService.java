package com.kaipai.module.server.ai.service;

public interface AiProviderSecretCryptoService {

    String encrypt(String plaintextJson);

    String decrypt(String envelopeJson);
}
