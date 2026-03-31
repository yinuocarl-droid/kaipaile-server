package com.kaipai.common.auth;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthContext {

    public AdminAuthenticatedUser requireCurrentAdmin() {
        AdminAuthenticatedUser admin = getCurrentAdmin();
        if (admin == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return admin;
    }

    public AdminAuthenticatedUser getCurrentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AdminAuthenticatedUser adminUser) {
            return adminUser;
        }
        return null;
    }

    public Long getCurrentAdminUserId() {
        AdminAuthenticatedUser admin = getCurrentAdmin();
        return admin == null ? null : admin.getAdminUserId();
    }
}
