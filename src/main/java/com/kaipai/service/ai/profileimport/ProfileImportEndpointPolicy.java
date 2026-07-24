package com.kaipai.service.ai.profileimport;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.function.Function;

/** Validates the fixed outbound DeepSeek destination before every request. */
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
        return address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }
}
