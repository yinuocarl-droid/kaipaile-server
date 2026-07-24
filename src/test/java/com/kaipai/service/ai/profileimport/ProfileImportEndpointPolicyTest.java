package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ProfileImportEndpointPolicyTest {
    private final ProfileImportEndpointPolicy policy = new ProfileImportEndpointPolicy();

    @Test
    void configuredEndpointMustBeTheControlledDeepSeekHttpsHost() {
        assertDoesNotThrow(() -> policy.validateConfigured(URI.create("https://api.deepseek.com/chat/completions")));
        assertThrows(IllegalArgumentException.class, () ->
                policy.validateConfigured(URI.create("http://api.deepseek.com/chat/completions")));
        assertThrows(IllegalArgumentException.class, () ->
                policy.validateConfigured(URI.create("https://evil.example/chat/completions")));
    }

    @Test
    void everyResolvedAddressMustBePublicAndGlobal() {
        Function<String, InetAddress[]> loopback = ignored ->
                new InetAddress[] {InetAddress.getLoopbackAddress()};
        assertThrows(IllegalArgumentException.class, () -> policy.validateResolved(
                URI.create("https://api.deepseek.com/chat/completions"), loopback));

        Function<String, InetAddress[]> publicAddress = ignored -> new InetAddress[] {
                address(new byte[] {8, 8, 8, 8})};
        assertDoesNotThrow(() -> policy.validateResolved(
                URI.create("https://api.deepseek.com/chat/completions"), publicAddress));
    }

    private InetAddress address(byte[] bytes) {
        try {
            return InetAddress.getByAddress("api.deepseek.com", bytes);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
