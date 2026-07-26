package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportConfigAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportConfigMapper;
import com.kaipai.model.ai.dto.ProfileImportPublicConfigUpdateDTO;
import com.kaipai.model.ai.dto.ProfileImportSecretUpdateDTO;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.model.ai.entity.AiProfileImportConfigAudit;
import com.kaipai.service.ai.AiProviderSecretCryptoService;
import com.kaipai.service.ai.ProfileImportConnectionTester;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class ProfileImportConfigServiceImplTest {
    private AiProfileImportConfigMapper mapper;
    private AiProfileImportConfigAuditMapper auditMapper;
    private AiProviderSecretCryptoService crypto;
    private ProfileImportConnectionTester tester;
    private ProfileImportConfigServiceImpl service;
    private AiProfileImportConfig stored;
    private ObjectMapper json;

    @BeforeEach
    void setup() {
        mapper = mock(AiProfileImportConfigMapper.class);
        auditMapper = mock(AiProfileImportConfigAuditMapper.class);
        crypto = mock(AiProviderSecretCryptoService.class);
        tester = mock(ProfileImportConnectionTester.class);
        json = new ObjectMapper();
        stored = configuredEntity();
        when(mapper.selectOne(any())).thenAnswer(invocation -> stored);
        when(mapper.selectByProviderCodeForUpdate("deepseek"))
                .thenAnswer(invocation -> stored);
        when(mapper.updateById(any())).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            return 1;
        });
        when(auditMapper.insert(any())).thenReturn(1);
        service = new ProfileImportConfigServiceImpl(mapper, auditMapper, crypto, tester, json);
    }

    @Test
    void secretIsEncryptedAndReadBackOnlyAsMask() {
        when(crypto.encrypt(any())).thenReturn("ciphertext");

        var result = service.saveSecret(9L, new ProfileImportSecretUpdateDTO("sk-test-value"));

        assertEquals("****alue", result.getSecretMask());
        assertFalse(result.toString().contains("sk-test-value"));
        assertEquals("ciphertext", stored.getSecretConfigCiphertext());
    }

    @Test
    void capabilityRequiresConfigSecretSuccessfulTestAndEnable() {
        service.savePublicConfig(9L, valid());
        var unavailable = service.capability();
        assertAll(
                () -> assertFalse(unavailable.isEnabled()),
                () -> assertFalse(unavailable.isAvailable()),
                () -> assertEquals("deepseek", unavailable.getProviderCode()),
                () -> assertEquals("deepseek-chat", unavailable.getModelName()),
                () -> assertEquals(20000, unavailable.getMaxInputLength()),
                () -> assertEquals("智能导入未启用", unavailable.getUnavailableReason()));
        when(crypto.encrypt(any())).thenReturn("cipher");
        service.saveSecret(9L, new ProfileImportSecretUpdateDTO("sk-test-value"));
        when(crypto.decrypt("cipher")).thenReturn("{\"apiKey\":\"sk-test-value\"}");
        service.testConnection(9L);
        service.setEnabled(9L, true);

        var available = service.capability();
        assertAll(
                () -> assertTrue(available.isEnabled()),
                () -> assertTrue(available.isAvailable()),
                () -> assertEquals("deepseek", available.getProviderCode()),
                () -> assertEquals("deepseek-chat", available.getModelName()),
                () -> assertEquals(20000, available.getMaxInputLength()),
                () -> assertEquals(null, available.getUnavailableReason()));
        verify(auditMapper, atLeast(2)).insert(any());
    }

    @Test
    void runtimeConfigDistinguishesDisabledFromEnabledButNotReady() {
        stored.setEnabled(false);
        assertEquals(46001,
                assertThrows(BizException.class, service::runtimeConfig).getCode());

        stored.setEnabled(true);
        stored.setSecretConfigCiphertext("cipher");
        stored.setLastTestStatus("failed");
        assertEquals(46002,
                assertThrows(BizException.class, service::runtimeConfig).getCode());
    }

    @Test
    void runtimeConfigCarriesTheExactPersistedConfigVersion() {
        stored.setVersion(17);
        stored.setEnabled(true);
        stored.setSecretConfigCiphertext("cipher");
        stored.setLastTestStatus("success");
        when(crypto.decrypt("cipher")).thenReturn("{\"apiKey\":\"sk-memory-only\"}");

        ProfileImportRuntimeConfig runtime = service.runtimeConfig();

        assertEquals(1L, runtime.configId());
        assertEquals(17, runtime.configVersion());
        assertTrue(runtime.toString().contains("configVersion=17"));
        assertFalse(runtime.toString().contains("sk-memory-only"));
    }

    @Test
    void everyConfigurationMutationLocksDeepSeekBeforeReadingOrChangingIt() {
        when(crypto.encrypt(any())).thenReturn("cipher");
        stored.setSecretConfigCiphertext("cipher");
        when(crypto.decrypt("cipher")).thenReturn("{\"apiKey\":\"sk-memory-only\"}");

        service.savePublicConfig(9L, valid());
        service.saveSecret(9L, new ProfileImportSecretUpdateDTO("sk-private-value"));
        service.testConnection(9L);
        stored.setLastTestStatus("success");
        service.setEnabled(9L, true);

        verify(mapper, org.mockito.Mockito.times(4))
                .selectByProviderCodeForUpdate("deepseek");
    }

    @Test
    void failedConnectionProbeCannotRecordSuccessfulTestStatus() {
        stored.setSecretConfigCiphertext("cipher");
        when(crypto.decrypt("cipher")).thenReturn("{\"apiKey\":\"sk-memory-only\"}");
        doThrow(new BizException("DeepSeek 连接测试失败"))
                .when(tester).test(stored, "sk-memory-only");
        ArgumentCaptor<AiProfileImportConfigAudit> audit =
                ArgumentCaptor.forClass(AiProfileImportConfigAudit.class);

        service.testConnection(9L);

        assertEquals("failed", stored.getLastTestStatus());
        assertEquals("连接失败", stored.getLastTestMessage());
        verify(auditMapper).insert(audit.capture());
        assertEquals("test", audit.getValue().getActionCode());
        assertEquals("failed", audit.getValue().getResultStatus());
    }

    @Test
    void cleanBootstrapPublicConfigRequiresExactlyOneInsertedRow() {
        stored = null;
        when(mapper.insert(any())).thenReturn(0);

        assertThrows(BizException.class, () -> service.savePublicConfig(9L, valid()));

        verifyNoInteractions(auditMapper);
    }

    @Test
    void publicConfigRejectsValuesOutsideGovernedBounds() {
        var connect = valid();
        connect.setConnectTimeoutMs(999);
        var read = valid();
        read.setReadTimeoutMs(180001);
        var input = valid();
        input.setMaxInputChars(50001);
        var output = valid();
        output.setMaxOutputTokens(999);
        var daily = valid();
        daily.setPerUserDailyLimit(101);
        var model = valid();
        model.setModelName(" ");

        assertAll(
                () -> assertThrows(RuntimeException.class, () -> service.savePublicConfig(9L, connect)),
                () -> assertThrows(RuntimeException.class, () -> service.savePublicConfig(9L, read)),
                () -> assertThrows(RuntimeException.class, () -> service.savePublicConfig(9L, input)),
                () -> assertThrows(RuntimeException.class, () -> service.savePublicConfig(9L, output)),
                () -> assertThrows(RuntimeException.class, () -> service.savePublicConfig(9L, daily)),
                () -> assertThrows(RuntimeException.class, () -> service.savePublicConfig(9L, model)));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void allConfigurationMutationsRollBackWhenPersistenceOrAuditFails() throws Exception {
        assertTransactional("savePublicConfig", Long.class, ProfileImportPublicConfigUpdateDTO.class);
        assertTransactional("saveSecret", Long.class, ProfileImportSecretUpdateDTO.class);
        assertTransactional("testConnection", Long.class);
        assertTransactional("setEnabled", Long.class, boolean.class);

        when(mapper.updateById(any())).thenReturn(0);
        assertThrows(BizException.class, () -> service.savePublicConfig(9L, valid()));
        verifyNoInteractions(auditMapper);

        when(mapper.updateById(any())).thenReturn(1);
        when(auditMapper.insert(any())).thenReturn(0);
        assertThrows(BizException.class, () -> service.savePublicConfig(9L, valid()));
    }

    @Test
    void auditKeepsSanitizedPublicAndSecretBeforeAfterSnapshots() throws Exception {
        stored.setEndpoint("https://api.deepseek.com/old");
        stored.setModelName("deepseek-old");
        stored.setSecretConfigCiphertext("cipher-old");
        stored.setSecretMaskJson("****old1");
        stored.setEnabled(true);
        stored.setLastTestStatus("success");
        ArgumentCaptor<AiProfileImportConfigAudit> audit =
                ArgumentCaptor.forClass(AiProfileImportConfigAudit.class);

        service.savePublicConfig(73L, valid());

        verify(auditMapper).insert(audit.capture());
        AiProfileImportConfigAudit publicAudit = audit.getValue();
        JsonNode beforePublic = json.readTree(publicAudit.getBeforePublicConfigJson());
        JsonNode afterPublic = json.readTree(publicAudit.getAfterPublicConfigJson());
        assertEquals(73L, publicAudit.getOperatorId());
        assertEquals("public_config_update", publicAudit.getActionCode());
        assertEquals("deepseek-old", beforePublic.path("modelName").asText());
        assertEquals("deepseek-chat", afterPublic.path("modelName").asText());
        assertFalse(afterPublic.path("enabled").asBoolean());
        assertEquals("****old1", publicAudit.getBeforeSecretMaskJson());
        assertEquals("****old1", publicAudit.getAfterSecretMaskJson());
        assertSanitized(publicAudit);

        clearInvocations(auditMapper);
        when(crypto.encrypt(any())).thenReturn("cipher-new");
        service.saveSecret(73L, new ProfileImportSecretUpdateDTO("sk-new-private"));

        verify(auditMapper).insert(audit.capture());
        AiProfileImportConfigAudit secretAudit = audit.getValue();
        assertEquals("secret_update", secretAudit.getActionCode());
        assertEquals("****old1", secretAudit.getBeforeSecretMaskJson());
        assertEquals("****vate", secretAudit.getAfterSecretMaskJson());
        assertSanitized(secretAudit);
    }

    @Test
    void cleanBootstrapRejectsSecretUntilPublicConfigurationExists() {
        stored = null;
        when(crypto.encrypt(any())).thenReturn("cipher");

        BizException error = assertThrows(BizException.class,
                () -> service.saveSecret(9L, new ProfileImportSecretUpdateDTO("sk-private-value")));

        assertTrue(error.getMessage().contains("先保存"));
        verifyNoInteractions(crypto);
        verify(mapper, never()).insert(any());
        verifyNoInteractions(auditMapper);
    }

    @Test
    void shortSecretsAreRejectedAndSecretBearingObjectsHaveSafeToString() {
        assertAll(
                () -> assertThrows(BizException.class,
                        () -> service.saveSecret(9L, new ProfileImportSecretUpdateDTO("x"))),
                () -> assertThrows(BizException.class,
                        () -> service.saveSecret(9L, new ProfileImportSecretUpdateDTO("abcd"))));
        verifyNoInteractions(crypto);
        verify(mapper, never()).updateById(any());
        verifyNoInteractions(auditMapper);

        String secretDto = new ProfileImportSecretUpdateDTO("sk-private-value").toString();
        String runtime = new ProfileImportRuntimeConfig(
                1L, 7, "https://api.deepseek.com/chat/completions", "deepseek-chat",
                "sk-private-value", 3000, 30000, 20000, 8000, 10).toString();
        assertFalse(secretDto.contains("sk-private-value"));
        assertFalse(runtime.contains("sk-private-value"));
        assertTrue(secretDto.contains("REDACTED"));
        assertTrue(runtime.contains("REDACTED"));
    }

    private void assertTransactional(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProfileImportConfigServiceImpl.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional, methodName + " must define a transaction boundary");
        assertTrue(java.util.List.of(transactional.rollbackFor()).contains(Exception.class));
    }

    private void assertSanitized(AiProfileImportConfigAudit audit) {
        String persisted = audit.toString();
        assertFalse(persisted.contains("sk-new-private"));
        assertFalse(persisted.contains("cipher-old"));
        assertFalse(persisted.contains("cipher-new"));
        assertFalse(audit.getBeforePublicConfigJson().contains("secret"));
        assertFalse(audit.getAfterPublicConfigJson().contains("secret"));
    }

    private AiProfileImportConfig configuredEntity() {
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setConfigId(1L);
        config.setProviderCode("deepseek");
        config.setDisplayName("DeepSeek 资料导入");
        config.setEnabled(false);
        config.setEndpoint("https://api.deepseek.com/chat/completions");
        config.setModelName("deepseek-chat");
        config.setConnectTimeoutMs(3000);
        config.setReadTimeoutMs(30000);
        config.setMaxInputChars(20000);
        config.setMaxOutputTokens(8000);
        config.setPerUserDailyLimit(10);
        config.setVersion(7);
        return config;
    }

    private ProfileImportPublicConfigUpdateDTO valid() {
        var config = new ProfileImportPublicConfigUpdateDTO();
        config.setEndpoint("https://api.deepseek.com/chat/completions");
        config.setModelName("deepseek-chat");
        config.setConnectTimeoutMs(3000);
        config.setReadTimeoutMs(30000);
        config.setMaxInputChars(20000);
        config.setMaxOutputTokens(8000);
        config.setPerUserDailyLimit(10);
        return config;
    }
}
