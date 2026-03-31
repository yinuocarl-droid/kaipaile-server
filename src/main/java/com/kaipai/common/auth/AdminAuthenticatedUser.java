package com.kaipai.common.auth;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class AdminAuthenticatedUser {

    Long adminUserId;
    String account;
    String userName;
    Set<String> roleCodes;
    Set<String> permissions;
}
