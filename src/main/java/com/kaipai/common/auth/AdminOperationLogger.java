package com.kaipai.common.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.service.system.AdminOperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminOperationLogger {

    static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final int REQUEST_ID_HASH_LENGTH = 32;
    private static final int REQUEST_ID_PREFIX_LENGTH = MAX_REQUEST_ID_LENGTH - REQUEST_ID_HASH_LENGTH - 1;

    private final AdminOperationLogService adminOperationLogService;
    private final AdminAuthContext adminAuthContext;
    private final ObjectMapper objectMapper;

    public void log(AdminOperationLogCommand command) {
        AdminAuthenticatedUser currentAdmin = adminAuthContext.getCurrentAdmin();
        HttpServletRequest request = currentRequest();

        AdminOperationLog logEntity = new AdminOperationLog();
        logEntity.setAdminUserId(currentAdmin == null ? 0L : currentAdmin.getAdminUserId());
        logEntity.setAdminUserName(currentAdmin == null ? "system" : currentAdmin.getUserName());
        logEntity.setModuleCode(command.getModuleCode());
        logEntity.setOperationCode(command.getOperationCode());
        logEntity.setTargetType(command.getTargetType());
        logEntity.setTargetId(command.getTargetId());
        logEntity.setRequestId(resolveRequestId(request, command.getRequestId()));
        logEntity.setClientIp(resolveClientIp(request));
        logEntity.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        logEntity.setBeforeSnapshotJson(toJson(command.getBeforeSnapshot()));
        logEntity.setAfterSnapshotJson(toJson(command.getAfterSnapshot()));
        logEntity.setExtraContextJson(toJson(command.getExtraContext()));
        logEntity.setOperationResult(command.getOperationResult() == null ? 1 : command.getOperationResult());
        logEntity.setFailReason(command.getFailReason());
        logEntity.setConfirmToken(command.getConfirmToken());
        logEntity.setConfirmedAt(command.getConfirmedAt());
        adminOperationLogService.save(logEntity);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private String resolveRequestId(HttpServletRequest request, String commandRequestId) {
        if (StringUtils.hasText(commandRequestId)) {
            return normalizeRequestId(commandRequestId);
        }
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        String requestId = request.getHeader("X-Request-Id");
        return normalizeRequestId(requestId);
    }

    static String normalizeRequestId(String requestId) {
        String candidate = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
        if (candidate.length() <= MAX_REQUEST_ID_LENGTH) {
            return candidate;
        }
        String hash = DigestUtils.md5DigestAsHex(candidate.getBytes(StandardCharsets.UTF_8));
        String normalized = candidate.substring(0, REQUEST_ID_PREFIX_LENGTH) + "-" + hash;
        log.warn("admin operation log request id exceeded {} chars, normalized to {}", candidate.length(), normalized);
        return normalized;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("serialize admin operation log payload failed", e);
            return String.valueOf(value);
        }
    }
}
