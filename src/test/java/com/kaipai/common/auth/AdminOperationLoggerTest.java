package com.kaipai.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.service.system.AdminOperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationLoggerTest {

    @Mock
    private AdminOperationLogService adminOperationLogService;

    @Mock
    private AdminAuthContext adminAuthContext;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void logShouldNormalizeOverlongRequestIdBeforeSave() {
        AdminOperationLogger logger = new AdminOperationLogger(adminOperationLogService, adminAuthContext, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        String rawRequestId = "20260404-013746-continue-ai-resume-governance-sweep-record-notification-extra-tail-for-db-and-even-more-padding-to-cross-the-128-char-limit";
        request.addHeader("X-Request-Id", rawRequestId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(adminAuthContext.getCurrentAdmin()).thenReturn(AdminAuthenticatedUser.builder()
                .adminUserId(7L)
                .account("admin")
                .userName("ops")
                .roleCodes(Set.of("ops"))
                .permissions(Set.of("ai:governance"))
                .build());
        when(adminOperationLogService.save(any(AdminOperationLog.class))).thenReturn(true);

        logger.log(AdminOperationLogCommand.builder()
                .moduleCode("ai_resume")
                .operationCode("record_notification")
                .targetType("ai_resume_failure")
                .targetId(1001L)
                .build());

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(adminOperationLogService).save(logCaptor.capture());
        String storedRequestId = logCaptor.getValue().getRequestId();
        assertEquals(AdminOperationLogger.MAX_REQUEST_ID_LENGTH, storedRequestId.length());
        assertTrue(storedRequestId.startsWith(rawRequestId.substring(0, 95)));
        assertTrue(storedRequestId.endsWith(org.springframework.util.DigestUtils.md5DigestAsHex(rawRequestId.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void normalizeRequestIdShouldKeepValueWhenWithinColumnLimit() {
        String rawRequestId = "20260404-014505-continue-ai-resume-governance-record-requestid-fix-record-notification";

        assertTrue(rawRequestId.length() < AdminOperationLogger.MAX_REQUEST_ID_LENGTH);
        assertEquals(rawRequestId, AdminOperationLogger.normalizeRequestId(rawRequestId));
    }

    @Test
    void logRequiredThrowsWhenSaveReturnsFalse() {
        AdminOperationLogger logger = logger();
        when(adminOperationLogService.save(any(AdminOperationLog.class))).thenReturn(false);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> logger.logRequired(sanitizedCommand()));

        assertEquals("required admin operation log was not persisted", error.getMessage());
        verify(adminOperationLogService).save(any(AdminOperationLog.class));
    }

    @Test
    void logRequiredPropagatesServiceFailureUnchanged() {
        AdminOperationLogger logger = logger();
        IllegalStateException failure = new IllegalStateException("db unavailable");
        when(adminOperationLogService.save(any(AdminOperationLog.class))).thenThrow(failure);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> logger.logRequired(sanitizedCommand()));

        assertSame(failure, error);
    }

    @Test
    void existingLogStillIgnoresFalseSaveResult() {
        AdminOperationLogger logger = logger();
        when(adminOperationLogService.save(any(AdminOperationLog.class))).thenReturn(false);

        assertDoesNotThrow(() -> logger.log(sanitizedCommand()));

        verify(adminOperationLogService).save(any(AdminOperationLog.class));
    }

    private AdminOperationLogger logger() {
        return new AdminOperationLogger(
                adminOperationLogService, adminAuthContext, new ObjectMapper());
    }

    private static AdminOperationLogCommand sanitizedCommand() {
        return AdminOperationLogCommand.builder()
                .moduleCode("ai-profile-import")
                .operationCode("prompt-publish")
                .targetType("ai_profile_import_prompt_template")
                .targetId(11L)
                .operationResult(1)
                .extraContext(Set.of("sanitized"))
                .build();
    }
}
