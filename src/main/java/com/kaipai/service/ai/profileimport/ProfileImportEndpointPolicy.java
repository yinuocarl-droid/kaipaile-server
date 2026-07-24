package com.kaipai.service.ai.profileimport;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Validates the fixed outbound DeepSeek destination before every request. */
@Component
public class ProfileImportEndpointPolicy {
    public void validateConfigured(URI endpoint) {
        if (endpoint == null
                || !"https".equalsIgnoreCase(endpoint.getScheme())
                || !"api.deepseek.com".equalsIgnoreCase(endpoint.getHost())
                || endpoint.getUserInfo() != null
                || endpoint.getPort() != -1) {
            throw new IllegalArgumentException("only the controlled DeepSeek HTTPS host is allowed");
        }
    }

    public void validateResolved(URI endpoint, Function<String, InetAddress[]> resolver) {
        validateConfigured(endpoint);
        InetAddress[] addresses;
        try {
            addresses = resolver.apply(endpoint.getHost());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("DeepSeek host resolution failed", error);
        }
        if (addresses == null || addresses.length == 0
                || Arrays.stream(addresses).anyMatch(this::isNonPublic)) {
            throw new IllegalArgumentException("DeepSeek host resolved to a non-public address");
        }
    }

    private boolean isNonPublic(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isNonPublicIpv4(bytes);
        }
        if (bytes.length != 16) {
            return true;
        }
        // Global unicast IPv6 is 2000::/3; documentation space is not routable.
        return (unsigned(bytes[0]) & 0xe0) != 0x20
                || matches(bytes, new int[] {0x20, 0x01, 0x0d, 0xb8}, 4);
    }

    private boolean isNonPublicIpv4(byte[] bytes) {
        int first = unsigned(bytes[0]);
        int second = unsigned(bytes[1]);
        int third = unsigned(bytes[2]);
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 192 && second == 88 && third == 99)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 224;
    }

    private boolean matches(byte[] bytes, int[] prefix, int length) {
        for (int index = 0; index < length; index++) {
            if (unsigned(bytes[index]) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}
