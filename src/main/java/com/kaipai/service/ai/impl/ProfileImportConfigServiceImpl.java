package com.kaipai.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportConfigAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportConfigMapper;
import com.kaipai.model.ai.dto.*;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.model.ai.entity.AiProfileImportConfigAudit;
import com.kaipai.service.ai.*;
import com.kaipai.service.ai.profileimport.ProfileImportEndpointPolicy;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileImportConfigServiceImpl implements ProfileImportConfigService {
    private final AiProfileImportConfigMapper mapper;
    private final AiProfileImportConfigAuditMapper auditMapper;
    private final AiProviderSecretCryptoService crypto;
    private final ProfileImportConnectionTester tester;
    private final ObjectMapper json;

    public ProfileImportConfigRespDTO adminConfig() { return dto(config()); }
    public List<ProfileImportConfigAuditRespDTO> audits() {
        return auditMapper.selectList(new LambdaQueryWrapper<AiProfileImportConfigAudit>()
                .orderByDesc(AiProfileImportConfigAudit::getAuditId).last("limit 50"))
                .stream().map(this::auditDto).toList();
    }
    @Transactional(rollbackFor = Exception.class)
    public ProfileImportConfigRespDTO savePublicConfig(Long op, ProfileImportPublicConfigUpdateDTO d) {
        validatePublicConfig(d);
        validateEndpoint(d.getEndpoint());
        AiProfileImportConfig c = config();
        String beforePublicConfig = publicSnapshot(c);
        String beforeSecretMask = c.getSecretMaskJson();
        c.setEndpoint(d.getEndpoint());
        c.setModelName(d.getModelName());
        c.setConnectTimeoutMs(d.getConnectTimeoutMs());
        c.setReadTimeoutMs(d.getReadTimeoutMs());
        c.setMaxInputChars(d.getMaxInputChars());
        c.setMaxOutputTokens(d.getMaxOutputTokens());
        c.setPerUserDailyLimit(d.getPerUserDailyLimit());
        reset(c);
        persist(c);
        audit(op, c, "public_config_update", "success", beforePublicConfig, beforeSecretMask);
        return dto(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProfileImportConfigRespDTO saveSecret(Long op, ProfileImportSecretUpdateDTO d) {
        if (d == null || !StringUtils.hasText(d.apiKey()) || d.apiKey().trim().length() <= 4) {
            throw new BizException("DeepSeek API Key 至少需要 5 个字符");
        }
        AiProfileImportConfig c = config();
        if (c.getConfigId() == null) {
            throw new BizException("请先保存 DeepSeek 公共配置");
        }
        String beforePublicConfig = publicSnapshot(c);
        String beforeSecretMask = c.getSecretMaskJson();
        try { c.setSecretConfigCiphertext(crypto.encrypt(json.writeValueAsString(java.util.Map.of("apiKey", d.apiKey())))); }
        catch (Exception error) { throw new BizException("密钥保存失败"); }
        c.setSecretMaskJson(mask(d.apiKey()));
        reset(c);
        persist(c);
        audit(op, c, "secret_update", "success", beforePublicConfig, beforeSecretMask);
        return dto(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProfileImportConfigRespDTO testConnection(Long op) {
        AiProfileImportConfig c = config();
        if (!StringUtils.hasText(c.getSecretConfigCiphertext())) throw new BizException("请先配置密钥");
        String beforePublicConfig = publicSnapshot(c);
        String beforeSecretMask = c.getSecretMaskJson();
        try { String key = json.readTree(crypto.decrypt(c.getSecretConfigCiphertext())).path("apiKey").asText(); tester.test(c, key); c.setLastTestStatus("success"); c.setLastTestMessage("连接成功"); }
        catch (Exception error) { c.setLastTestStatus("failed"); c.setLastTestMessage("连接失败"); }
        c.setLastTestAt(LocalDateTime.now());
        persist(c);
        audit(op, c, "test", c.getLastTestStatus(), beforePublicConfig, beforeSecretMask);
        return dto(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProfileImportConfigRespDTO setEnabled(Long op, boolean enabled) {
        AiProfileImportConfig c = config();
        if (enabled && !ready(c)) throw new BizException("配置未通过连接测试");
        String beforePublicConfig = publicSnapshot(c);
        String beforeSecretMask = c.getSecretMaskJson();
        c.setEnabled(enabled);
        persist(c);
        audit(op, c, enabled ? "enable" : "disable", "success", beforePublicConfig, beforeSecretMask);
        return dto(c);
    }
    public ProfileImportCapabilityRespDTO capability() {
        AiProfileImportConfig c = config();
        boolean enabled = Boolean.TRUE.equals(c.getEnabled());
        boolean available = enabled && ready(c);
        return new ProfileImportCapabilityRespDTO(
                enabled,
                available,
                c.getProviderCode(),
                c.getModelName(),
                c.getMaxInputChars(),
                available ? null : unavailableReason(c));
    }
    public ProfileImportRuntimeConfig runtimeConfig() {
        AiProfileImportConfig c;
        try {
            c = config();
        } catch (RuntimeException error) {
            throw com.kaipai.model.actor.dto.ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        if (!Boolean.TRUE.equals(c.getEnabled())) {
            throw com.kaipai.model.actor.dto.ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.toException();
        }
        if (!ready(c)) {
            throw com.kaipai.model.actor.dto.ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        try {
            String key = json.readTree(crypto.decrypt(c.getSecretConfigCiphertext())).path("apiKey").asText();
            if (!StringUtils.hasText(key)) {
                throw com.kaipai.model.actor.dto.ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
            }
            return new ProfileImportRuntimeConfig(
                    c.getConfigId(), c.getEndpoint(), c.getModelName(), key,
                    c.getConnectTimeoutMs(), c.getReadTimeoutMs(), c.getMaxInputChars(),
                    c.getMaxOutputTokens(), c.getPerUserDailyLimit());
        } catch (Exception error) {
            throw com.kaipai.model.actor.dto.ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
    }

    private AiProfileImportConfig config() { AiProfileImportConfig c = mapper.selectOne(new LambdaQueryWrapper<AiProfileImportConfig>().eq(AiProfileImportConfig::getProviderCode, "deepseek").last("limit 1")); if (c == null) { c = new AiProfileImportConfig(); c.setProviderCode("deepseek"); c.setDisplayName("DeepSeek 资料导入"); c.setEnabled(false); } return c; }
    private boolean ready(AiProfileImportConfig c) { return StringUtils.hasText(c.getEndpoint()) && StringUtils.hasText(c.getModelName()) && StringUtils.hasText(c.getSecretConfigCiphertext()) && "success".equals(c.getLastTestStatus()); }
    private void reset(AiProfileImportConfig c) { c.setEnabled(false); c.setLastTestStatus(null); c.setLastTestAt(null); c.setLastTestMessage(null); }
    private void persist(AiProfileImportConfig c) {
        int affected = c.getConfigId() == null ? mapper.insert(c) : mapper.updateById(c);
        if (affected != 1) {
            throw new BizException("DeepSeek 配置保存失败");
        }
    }
    private void audit(
            Long op,
            AiProfileImportConfig c,
            String action,
            String status,
            String beforePublicConfig,
            String beforeSecretMask) {
        AiProfileImportConfigAudit a = new AiProfileImportConfigAudit();
        a.setConfigId(c.getConfigId());
        a.setActionCode(action);
        a.setBeforePublicConfigJson(beforePublicConfig);
        a.setAfterPublicConfigJson(publicSnapshot(c));
        a.setBeforeSecretMaskJson(beforeSecretMask);
        a.setAfterSecretMaskJson(c.getSecretMaskJson());
        a.setOperatorId(op);
        a.setResultStatus(status);
        if (auditMapper.insert(a) != 1) {
            throw new BizException("DeepSeek 配置审计保存失败");
        }
    }
    private void validateEndpoint(String endpoint) { try { new ProfileImportEndpointPolicy().validateConfigured(URI.create(endpoint)); } catch (Exception error) { throw new BizException("仅允许 DeepSeek HTTPS 官方接口"); } }
    private void validatePublicConfig(ProfileImportPublicConfigUpdateDTO d) {
        if (d == null || !StringUtils.hasText(d.getModelName()) || d.getModelName().trim().length() > 128
                || !between(d.getConnectTimeoutMs(), 1000, 30000)
                || !between(d.getReadTimeoutMs(), 5000, 180000)
                || !between(d.getMaxInputChars(), 1000, 50000)
                || !between(d.getMaxOutputTokens(), 1000, 16000)
                || !between(d.getPerUserDailyLimit(), 1, 100)) {
            throw new BizException("DeepSeek 配置参数超出允许范围");
        }
    }
    private boolean between(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }
    private String mask(String key) {
        String normalized = key.trim();
        return "****" + normalized.substring(normalized.length() - 4);
    }
    private String unavailableReason(AiProfileImportConfig c) {
        if (!Boolean.TRUE.equals(c.getEnabled())) {
            return "智能导入未启用";
        }
        return "智能导入配置未通过连接测试";
    }
    private String publicSnapshot(AiProfileImportConfig c) {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("providerCode", c.getProviderCode());
        snapshot.put("displayName", c.getDisplayName());
        put(snapshot, "enabled", c.getEnabled());
        snapshot.put("endpoint", c.getEndpoint());
        snapshot.put("modelName", c.getModelName());
        put(snapshot, "connectTimeoutMs", c.getConnectTimeoutMs());
        put(snapshot, "readTimeoutMs", c.getReadTimeoutMs());
        put(snapshot, "maxInputChars", c.getMaxInputChars());
        put(snapshot, "maxOutputTokens", c.getMaxOutputTokens());
        put(snapshot, "perUserDailyLimit", c.getPerUserDailyLimit());
        snapshot.put("lastTestStatus", c.getLastTestStatus());
        snapshot.put("lastTestMessage", c.getLastTestMessage());
        snapshot.put("lastTestAt", c.getLastTestAt() == null ? null : c.getLastTestAt().toString());
        return snapshot.toString();
    }
    private void put(ObjectNode target, String field, Integer value) {
        if (value == null) target.putNull(field); else target.put(field, value);
    }
    private void put(ObjectNode target, String field, Boolean value) {
        if (value == null) target.putNull(field); else target.put(field, value);
    }
    private ProfileImportConfigRespDTO dto(AiProfileImportConfig c) { ProfileImportConfigRespDTO d = new ProfileImportConfigRespDTO(); d.setEnabled(Boolean.TRUE.equals(c.getEnabled())); d.setAvailable(Boolean.TRUE.equals(c.getEnabled()) && ready(c)); d.setEndpoint(c.getEndpoint()); d.setModelName(c.getModelName()); d.setConnectTimeoutMs(c.getConnectTimeoutMs()); d.setReadTimeoutMs(c.getReadTimeoutMs()); d.setMaxInputChars(c.getMaxInputChars()); d.setMaxOutputTokens(c.getMaxOutputTokens()); d.setPerUserDailyLimit(c.getPerUserDailyLimit()); d.setSecretMask(c.getSecretMaskJson()); d.setLastTestStatus(c.getLastTestStatus()); d.setLastTestMessage(c.getLastTestMessage()); d.setLastTestAt(c.getLastTestAt()); return d; }
    private ProfileImportConfigAuditRespDTO auditDto(AiProfileImportConfigAudit a) { ProfileImportConfigAuditRespDTO d = new ProfileImportConfigAuditRespDTO(); d.setAuditId(a.getAuditId()); d.setActionCode(a.getActionCode()); d.setOperatorId(a.getOperatorId()); d.setOperatorName(a.getOperatorName()); d.setResultStatus(a.getResultStatus()); d.setMessage(a.getMessage()); d.setCreateTime(a.getCreateTime()); return d; }
}
