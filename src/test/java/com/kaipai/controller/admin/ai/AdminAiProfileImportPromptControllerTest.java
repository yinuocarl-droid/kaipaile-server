package com.kaipai.controller.admin.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.TextNode;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.exception.GlobalExceptionHandler;
import com.kaipai.model.ai.dto.ProfileImportPromptCreateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptRestoreReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptUpdateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionActionReqDTO;
import com.kaipai.service.ai.ProfileImportPromptManagementService;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

class AdminAiProfileImportPromptControllerTest {

    private static final String ROOT = "/admin/ai/profile-import/prompt-templates";
    private static final String PAGE = "hasAuthority('page.system.ai-profile-import')";
    private static final String READ = PAGE
            + " and hasAuthority('action.system.ai-profile-import.template-read')";
    private static final String UPDATE = PAGE
            + " and hasAuthority('action.system.ai-profile-import.template-update')";
    private static final String TEST = PAGE
            + " and hasAuthority('action.system.ai-profile-import.template-test')";
    private static final String PUBLISH = PAGE
            + " and hasAuthority('action.system.ai-profile-import.template-publish')";
    private static final String RESTORE = PAGE
            + " and hasAuthority('action.system.ai-profile-import.template-restore')";
    private static final String AUDIT = PAGE
            + " and hasAuthority('action.system.ai-profile-import.audit')";

    @Test
    void writeEndpointsUseOnlyTheAuthenticatedAdminIdAndKeepRestoreVersionsDistinct() {
        Fixture fixture = fixture();
        ProfileImportPromptCreateDraftReqDTO create = new ProfileImportPromptCreateDraftReqDTO();
        create.setExpectedTemplateVersion(8);
        ProfileImportPromptUpdateDraftReqDTO update = new ProfileImportPromptUpdateDraftReqDTO();
        update.setExpectedVersion(3);
        ProfileImportPromptVersionActionReqDTO abandon = action(
                "DRAFT_INVALID", 8, 3);
        ProfileImportPromptVersionActionReqDTO publish = action(
                "INITIAL_RELEASE", 8, 3);
        ProfileImportPromptRestoreReqDTO restore = new ProfileImportPromptRestoreReqDTO();
        restore.setReasonCode("INCIDENT_ROLLBACK");
        restore.setExpectedTemplateVersion(8);

        fixture.controller().createDraft("full_profile", create);
        fixture.controller().updateDraft(101L, update);
        fixture.controller().abandonDraft(101L, abandon);
        fixture.controller().test(101L);
        fixture.controller().publish(101L, publish);
        fixture.controller().restore("full_profile", 101L, restore);

        verify(fixture.service()).createDraft(73L, "full_profile", create);
        verify(fixture.service()).updateDraft(73L, 101L, update);
        verify(fixture.service()).abandonDraft(73L, 101L, abandon);
        verify(fixture.service()).test(73L, 101L);
        verify(fixture.service()).publish(73L, 101L, publish);
        verify(fixture.service()).restore(73L, "full_profile", 101L, restore);
    }

    @Test
    void listEndpointsDelegateOnlyToSummaryAndAuditQueries() {
        Fixture fixture = fixture();

        fixture.controller().templates();
        fixture.controller().versions("works_only");
        fixture.controller().audits();

        verify(fixture.service()).templates();
        verify(fixture.service()).versions("works_only");
        verify(fixture.service()).audits();
    }

    @Test
    void everyRequestBodyRejectsUnexpectedFieldsBeforeServiceInvocation() {
        Fixture fixture = fixture();
        TextNode rejected = TextNode.valueOf("SENSITIVE_REJECTED_VALUE");
        ProfileImportPromptCreateDraftReqDTO create = new ProfileImportPromptCreateDraftReqDTO();
        create.captureUnexpectedField("operatorId", rejected);
        ProfileImportPromptUpdateDraftReqDTO update = new ProfileImportPromptUpdateDraftReqDTO();
        update.captureUnexpectedField("state", rejected);
        ProfileImportPromptVersionActionReqDTO abandon = action("DRAFT_INVALID", 8, 3);
        abandon.captureUnexpectedField("reason", rejected);
        ProfileImportPromptVersionActionReqDTO publish = action("INITIAL_RELEASE", 8, 3);
        publish.captureUnexpectedField("contentSha256", rejected);
        ProfileImportPromptRestoreReqDTO restore = new ProfileImportPromptRestoreReqDTO();
        restore.setReasonCode("INCIDENT_ROLLBACK");
        restore.setExpectedTemplateVersion(8);
        restore.captureUnexpectedField("testOperatorId", rejected);

        assertPromptInvalid(() -> fixture.controller().createDraft("full_profile", create));
        assertPromptInvalid(() -> fixture.controller().updateDraft(101L, update));
        assertPromptInvalid(() -> fixture.controller().abandonDraft(101L, abandon));
        assertPromptInvalid(() -> fixture.controller().publish(101L, publish));
        assertPromptInvalid(() -> fixture.controller().restore("full_profile", 101L, restore));

        verifyNoInteractions(fixture.service());
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("invalidActionBodies")
    void actionRoutesRejectSensitiveUnknownBlankAndWrongSubsetValuesWithoutEchoOrDelegation(
            String actionName,
            String caseName,
            String path,
            String body,
            String rejectedValue) throws Exception {
        Fixture fixture = fixture();

        MvcResult result = fixture.mockMvc().perform(post(ROOT + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(46019))
                .andExpect(jsonPath("$.errorCode").value("PROFILE_IMPORT_PROMPT_INVALID"))
                .andExpect(jsonPath("$.message").value("Prompt 模板或操作参数无效"))
                .andReturn();

        assertFalse(
                result.getResponse().getContentAsString().contains(rejectedValue),
                actionName + ":" + caseName);
        verifyNoInteractions(fixture.service());
    }

    @ParameterizedTest(name = "request binding rejects {0}")
    @MethodSource("unreadableRequestBodies")
    void unreadableAndMissingBodiesUseStablePrivateEnvelopeBeforeServiceInvocation(
            String caseName,
            String httpMethod,
            String path,
            String body,
            String rejectedValue) throws Exception {
        Fixture fixture = fixture();
        MockHttpServletRequestBuilder request = "PUT".equals(httpMethod)
                ? put(ROOT + path)
                : post(ROOT + path);
        request.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }

        MvcResult result = fixture.mockMvc().perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(46019))
                .andExpect(jsonPath("$.errorCode").value("PROFILE_IMPORT_PROMPT_INVALID"))
                .andExpect(jsonPath("$.message").value("Prompt 模板或操作参数无效"))
                .andReturn();

        if (rejectedValue != null) {
            assertFalse(
                    result.getResponse().getContentAsString().contains(rejectedValue),
                    caseName);
        }
        verifyNoInteractions(fixture.service());
    }

    @Test
    void controllerSurfaceAndPermissionExpressionsAreExact() throws Exception {
        RequestMapping root = AdminAiProfileImportPromptController.class
                .getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[] {ROOT}, root.value());

        assertGet("templates", types(), values(), READ);
        assertGet("versions", types(String.class), values("/{templateCode}/versions"), READ);
        assertGet("version", types(Long.class), values("/versions/{versionId}"), READ);
        assertPost(
                "createDraft",
                types(String.class, ProfileImportPromptCreateDraftReqDTO.class),
                values("/{templateCode}/drafts"),
                UPDATE);
        assertPut(
                "updateDraft",
                types(Long.class, ProfileImportPromptUpdateDraftReqDTO.class),
                values("/versions/{versionId}"),
                UPDATE);
        assertPost(
                "abandonDraft",
                types(Long.class, ProfileImportPromptVersionActionReqDTO.class),
                values("/versions/{versionId}/abandon"),
                UPDATE);
        assertPost("test", types(Long.class), values("/versions/{versionId}/test"), TEST);
        assertPost(
                "publish",
                types(Long.class, ProfileImportPromptVersionActionReqDTO.class),
                values("/versions/{versionId}/publish"),
                PUBLISH);
        assertPost(
                "restore",
                types(String.class, Long.class, ProfileImportPromptRestoreReqDTO.class),
                values("/{templateCode}/versions/{versionId}/restore"),
                RESTORE);
        assertGet("audits", types(), values("/audits"), AUDIT);
        assertRequestBodyOptional(
                "createDraft",
                types(String.class, ProfileImportPromptCreateDraftReqDTO.class),
                1);
        assertRequestBodyOptional(
                "updateDraft",
                types(Long.class, ProfileImportPromptUpdateDraftReqDTO.class),
                1);
        assertRequestBodyOptional(
                "abandonDraft",
                types(Long.class, ProfileImportPromptVersionActionReqDTO.class),
                1);
        assertRequestBodyOptional(
                "publish",
                types(Long.class, ProfileImportPromptVersionActionReqDTO.class),
                1);
        assertRequestBodyOptional(
                "restore",
                types(String.class, Long.class, ProfileImportPromptRestoreReqDTO.class),
                2);
    }

    private static Stream<Arguments> invalidActionBodies() {
        Stream.Builder<Arguments> cases = Stream.builder();
        addInvalidActionBodies(
                cases,
                "abandon",
                "/versions/101/abandon",
                "DRAFT_INVALID",
                "INITIAL_RELEASE",
                true);
        addInvalidActionBodies(
                cases,
                "publish",
                "/versions/101/publish",
                "INITIAL_RELEASE",
                "INCIDENT_ROLLBACK",
                true);
        addInvalidActionBodies(
                cases,
                "restore",
                "/full_profile/versions/101/restore",
                "INCIDENT_ROLLBACK",
                "DRAFT_INVALID",
                false);
        return cases.build();
    }

    private static Stream<Arguments> unreadableRequestBodies() {
        return Stream.of(
                Arguments.of(
                        "empty restore body",
                        "POST",
                        "/full_profile/versions/101/restore",
                        null,
                        null),
                Arguments.of(
                        "JSON null restore body",
                        "POST",
                        "/full_profile/versions/101/restore",
                        "null",
                        null),
                Arguments.of(
                        "malformed restore JSON",
                        "POST",
                        "/full_profile/versions/101/restore",
                        "{\"reasonCode\":\"SENSITIVE_MALFORMED_VALUE\"",
                        "SENSITIVE_MALFORMED_VALUE"),
                Arguments.of(
                        "object reasonCode",
                        "POST",
                        "/full_profile/versions/101/restore",
                        """
                        {"reasonCode":{"secret":"SENSITIVE_REASON_OBJECT"},
                         "expectedTemplateVersion":8}
                        """,
                        "SENSITIVE_REASON_OBJECT"),
                Arguments.of(
                        "string publish version",
                        "POST",
                        "/versions/101/publish",
                        """
                        {"reasonCode":"INITIAL_RELEASE","expectedTemplateVersion":8,
                         "expectedVersion":"SENSITIVE_VERSION_VALUE"}
                        """,
                        "SENSITIVE_VERSION_VALUE"),
                Arguments.of(
                        "object update version",
                        "PUT",
                        "/versions/101",
                        """
                        {"expectedVersion":{"secret":"SENSITIVE_VERSION_OBJECT"}}
                        """,
                        "SENSITIVE_VERSION_OBJECT"));
    }

    private static void addInvalidActionBodies(
            Stream.Builder<Arguments> cases,
            String actionName,
            String path,
            String validReason,
            String wrongSubsetReason,
            boolean includeExpectedVersion) {
        String[][] invalidReasons = {
            {"blank reasonCode", "   "},
            {"api key-shaped reasonCode", "sk-live-SENSITIVE-KEY-123"},
            {"user text-shaped reasonCode", "USER_CLIPBOARD_PRIVATE_TEXT"},
            {"fixture text-shaped reasonCode", "FIXTURE_BODY_PRIVATE_TEXT"},
            {"Prompt text-shaped reasonCode", "SYSTEM_PROMPT_PRIVATE_TEXT"},
            {"wrong action subset", wrongSubsetReason}
        };
        for (String[] invalid : invalidReasons) {
            cases.add(Arguments.of(
                    actionName,
                    invalid[0],
                    path,
                    actionBody(invalid[1], includeExpectedVersion, null, null),
                    invalid[1]));
        }

        String[][] unexpectedFields = {
            {"free reason field", "reason", "FREE_REASON_SECRET"},
            {"unknown state", "state", "released-secret"},
            {"unknown hash", "contentSha256", "SENSITIVE_HASH_VALUE"},
            {"unknown operator", "operatorId", "SENSITIVE_OPERATOR_VALUE"}
        };
        for (String[] unexpected : unexpectedFields) {
            cases.add(Arguments.of(
                    actionName,
                    unexpected[0],
                    path,
                    actionBody(
                            validReason,
                            includeExpectedVersion,
                            unexpected[1],
                            unexpected[2]),
                    unexpected[2]));
        }
    }

    private static String actionBody(
            String reasonCode,
            boolean includeExpectedVersion,
            String unexpectedField,
            String unexpectedValue) {
        StringBuilder body = new StringBuilder()
                .append("{\"reasonCode\":\"")
                .append(reasonCode)
                .append("\",\"expectedTemplateVersion\":8");
        if (includeExpectedVersion) {
            body.append(",\"expectedVersion\":3");
        }
        if (unexpectedField != null) {
            body.append(",\"")
                    .append(unexpectedField)
                    .append("\":\"")
                    .append(unexpectedValue)
                    .append('"');
        }
        return body.append('}').toString();
    }

    private static ProfileImportPromptVersionActionReqDTO action(
            String reasonCode,
            int expectedTemplateVersion,
            int expectedVersion) {
        ProfileImportPromptVersionActionReqDTO request =
                new ProfileImportPromptVersionActionReqDTO();
        request.setReasonCode(reasonCode);
        request.setExpectedTemplateVersion(expectedTemplateVersion);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    private static void assertPromptInvalid(Executable executable) {
        BizException error = assertThrows(BizException.class, executable);
        assertEquals(46019, error.getCode());
        assertEquals("Prompt 模板或操作参数无效", error.getMessage());
    }

    private static void assertGet(
            String methodName,
            Class<?>[] parameterTypes,
            String[] paths,
            String permission) throws Exception {
        Method method = AdminAiProfileImportPromptController.class
                .getDeclaredMethod(methodName, parameterTypes);
        assertArrayEquals(paths, method.getAnnotation(GetMapping.class).value());
        assertEquals(permission, method.getAnnotation(PreAuthorize.class).value());
    }

    private static void assertPost(
            String methodName,
            Class<?>[] parameterTypes,
            String[] paths,
            String permission) throws Exception {
        Method method = AdminAiProfileImportPromptController.class
                .getDeclaredMethod(methodName, parameterTypes);
        assertArrayEquals(paths, method.getAnnotation(PostMapping.class).value());
        assertEquals(permission, method.getAnnotation(PreAuthorize.class).value());
    }

    private static void assertPut(
            String methodName,
            Class<?>[] parameterTypes,
            String[] paths,
            String permission) throws Exception {
        Method method = AdminAiProfileImportPromptController.class
                .getDeclaredMethod(methodName, parameterTypes);
        assertArrayEquals(paths, method.getAnnotation(PutMapping.class).value());
        assertEquals(permission, method.getAnnotation(PreAuthorize.class).value());
    }

    private static void assertRequestBodyOptional(
            String methodName,
            Class<?>[] parameterTypes,
            int bodyParameterIndex) throws Exception {
        Method method = AdminAiProfileImportPromptController.class
                .getDeclaredMethod(methodName, parameterTypes);
        RequestBody requestBody = method.getParameters()[bodyParameterIndex]
                .getAnnotation(RequestBody.class);
        assertEquals(false, requestBody.required(), methodName);
    }

    private static Class<?>[] types(Class<?>... values) {
        return values;
    }

    private static String[] values(String... values) {
        return values;
    }

    private static Fixture fixture() {
        ProfileImportPromptManagementService service =
                mock(ProfileImportPromptManagementService.class);
        AdminAuthContext authContext = mock(AdminAuthContext.class);
        AdminAuthenticatedUser admin = AdminAuthenticatedUser.builder()
                .adminUserId(73L)
                .account("ops")
                .userName("Prompt Admin")
                .roleCodes(Set.of("system-admin"))
                .permissions(Set.of())
                .build();
        when(authContext.requireCurrentAdmin()).thenReturn(admin);
        AdminAiProfileImportPromptController controller =
                new AdminAiProfileImportPromptController(service, authContext);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        return new Fixture(service, controller, mockMvc);
    }

    private record Fixture(
            ProfileImportPromptManagementService service,
            AdminAiProfileImportPromptController controller,
            MockMvc mockMvc) {}
}
