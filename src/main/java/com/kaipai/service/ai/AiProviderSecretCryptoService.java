package com.kaipai.service.ai;

public interface AiProviderSecretCryptoService {

    String encrypt(String plaintextJson);

    String decrypt(String envelopeJson);
}
