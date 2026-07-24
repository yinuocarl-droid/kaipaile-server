package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

class ProfileImportEndpointPolicyTest {
    private final ProfileImportEndpointPolicy policy = new ProfileImportEndpointPolicy();

    @Test
    void policyIsAConstructorInjectableSpringBean() {
        assertTrue(ProfileImportEndpointPolicy.class.isAnnotationPresent(Component.class));
    }

    @Test
    void configuredEndpointMustBeTheControlledDeepSeekHttpsHost() {
        assertDoesNotThrow(() -> policy.validateConfigured(URI.create("https://api.deepseek.com/chat/completions")));
        assertThrows(IllegalArgumentException.class, () ->
                policy.validateConfigured(URI.create("http://api.deepseek.com/chat/completions")));
        assertThrows(IllegalArgumentException.class, () ->
                policy.validateConfigured(URI.create("https://evil.example/chat/completions")));
        assertThrows(IllegalArgumentException.class, () ->
                policy.validateConfigured(URI.create("https://api.deepseek.com:443/chat/completions")));
        assertThrows(IllegalArgumentException.class, () ->
                policy.validateConfigured(URI.create("https://user@api.deepseek.com/chat/completions")));
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

    @Test
    void specialUseAddressesAreNotTreatedAsPublicDestinations() {
        List<byte[]> nonPublicAddresses = List.of(
                new byte[] {0, 0, 0, 1},
                new byte[] {10, 0, 0, 1},
                new byte[] {100, 64, 0, 1},
                new byte[] {(byte) 127, 0, 0, 1},
                new byte[] {(byte) 169, (byte) 254, 1, 1},
                new byte[] {(byte) 172, 16, 0, 1},
                new byte[] {(byte) 192, 0, 0, 1},
                new byte[] {(byte) 192, 0, 2, 1},
                new byte[] {(byte) 192, (byte) 168, 0, 1},
                new byte[] {(byte) 198, 18, 0, 1},
                new byte[] {(byte) 198, 51, 100, 1},
                new byte[] {(byte) 203, 0, 113, 1},
                new byte[] {(byte) 224, 0, 0, 1},
                new byte[] {(byte) 240, 0, 0, 1});

        for (byte[] bytes : nonPublicAddresses) {
            assertThrows(IllegalArgumentException.class, () -> policy.validateResolved(
                    URI.create("https://api.deepseek.com/chat/completions"),
                    ignored -> new InetAddress[] {address(bytes)}));
        }
    }

    @Test
    void onlyGlobalUnicastIpv6AddressesAreAccepted() {
        for (String value : List.of("::1", "fc00::1", "fe80::1", "ff02::1", "2001:db8::1")) {
            assertThrows(IllegalArgumentException.class, () -> policy.validateResolved(
                    URI.create("https://api.deepseek.com/chat/completions"),
                    ignored -> new InetAddress[] {address(value)}));
        }
        assertDoesNotThrow(() -> policy.validateResolved(
                URI.create("https://api.deepseek.com/chat/completions"),
                ignored -> new InetAddress[] {address("2606:4700:4700::1111")}));
    }

    private InetAddress address(byte[] bytes) {
        try {
            return InetAddress.getByAddress("api.deepseek.com", bytes);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
