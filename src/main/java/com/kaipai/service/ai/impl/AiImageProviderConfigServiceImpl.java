package com.kaipai.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.dto.AdminAiImageProviderActionDTO;
import com.kaipai.model.ai.dto.AdminAiImageProviderDTO;
import com.kaipai.model.ai.dto.AdminAiImageProviderPublicConfigSaveDTO;
import com.kaipai.model.ai.dto.AdminAiImageProviderRevealSecretRespDTO;
import com.kaipai.model.ai.dto.AdminAiImageProviderSaveDTO;
import com.kaipai.model.ai.dto.AdminAiImageProviderSecretSaveDTO;
import com.kaipai.model.ai.dto.AiImageProviderPublicConfigDTO;
import com.kaipai.model.ai.entity.AiImageProviderConfig;
import com.kaipai.model.ai.entity.AiImageProviderConfigAudit;
import com.kaipai.service.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.mapper.ai.AiImageProviderConfigAuditMapper;
import com.kaipai.mapper.ai.AiImageProviderConfigMapper;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.AiProviderSecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiImageProviderConfigServiceImpl
        extends ServiceImpl<AiImageProviderConfigMapper, AiImageProviderConfig>
        implements AiImageProviderConfigService {

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> SUPPORTED_PROVIDER_CODES = Set.of(
            "kplyyk",
            "http",
            "openai",
            "volc-seedream",
            "aliyun-qwen-image",
            "aliyun-wanxiang",
            "tencent-hunyuan",
            "baidu-qianfan"
    );
    private static final Map<String, List<String>> REQUIRED_SECRET_FIELDS = Map.of(
            "kplyyk", List.of("authToken"),
            "http", List.of("authToken"),
            "openai", List.of("apiKey"),
            "volc-seedream", List.of("apiKey"),
            "aliyun-qwen-image", List.of("apiKey"),
            "aliyun-wanxiang", List.of("apiKey"),
            "tencent-hunyuan", List.of("secretId", "secretKey"),
            "baidu-qianfan", List.of("apiKey")
    );
    private static final Map<String, List<String>> REQUIRED_PUBLIC_FIELDS = Map.of(
            "tencent-hunyuan", List.of("endpoint", "region", "model"),
            "kplyyk", List.of("endpoint", "model"),
            "http", List.of("endpoint", "model"),
            "openai", List.of("endpoint", "model"),
            "volc-seedream", List.of("endpoint", "model"),
            "aliyun-qwen-image", List.of("endpoint", "model"),
            "aliyun-wanxiang", List.of("endpoint", "model"),
            "baidu-qianfan", List.of("endpoint", "model")
    );
    private static final Map<String, String> DEFAULT_DISPLAY_NAMES = Map.of(
            "kplyyk", "KPLYYK 管理 API",
            "http", "通用 HTTP Provider",
            "openai", "OpenAI Images",
            "volc-seedream", "火山/豆包 Seedream",
            "aliyun-qwen-image", "阿里云百炼 Qwen Image",
            "aliyun-wanxiang", "阿里云通义万相",
            "tencent-hunyuan", "腾讯混元生图",
            "baidu-qianfan", "百度千帆图像生成"
    );
    private static final Map<String, Integer> DEFAULT_PRIORITIES = Map.of(
            "kplyyk", 10,
            "volc-seedream", 20,
            "aliyun-qwen-image", 30,
            "aliyun-wanxiang", 40,
            "tencent-hunyuan", 50,
            "baidu-qianfan", 60,
            "http", 90,
            "openai", 100
    );

    private final ObjectMapper objectMapper;
    private final AiProviderSecretCryptoService secretCryptoService;
    private final AiImageProviderConfigAuditMapper auditMapper;
    private final AdminAuthContext adminAuthContext;
    private final AdminOperationLogger adminOperationLogger;

    @Override
    public List<AdminAiImageProviderDTO> adminList() {
        return listOrdered().stream().map(this::toAdminDto).toList();
    }

    @Override
    public AdminAiImageProviderDTO adminDetail(String providerCode) {
        return toAdminDto(requireConfig(providerCode));
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO saveProvider(AdminAiImageProviderSaveDTO request) {
        if (request == null) {
            throw new BizException("厂商接入信息不能为空");
        }
        String providerCode = normalizeProviderCode(request.getProviderCode());
        Optional<AiImageProviderConfig> existing = findByProviderCode(providerCode);
        AiImageProviderConfig config = existing.orElseGet(AiImageProviderConfig::new);
        AdminAiImageProviderDTO before = existing.map(this::toAdminDto).orElse(null);

        config.setProviderCode(providerCode);
        config.setDisplayName(resolveDisplayName(providerCode, request.getDisplayName()));
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        config.setActive(existing.map(AiImageProviderConfig::getActive).orElse(0));
        config.setPriority(resolvePriority(providerCode, request.getPriority()));
        config.setPublicConfigJson(writeJson(normalizePublicConfig(
                request.getPublicConfig(),
                existing.map(this::readPublicConfig).orElseGet(AiImageProviderPublicConfigDTO::new)
        )));

        Map<String, String> submittedSecrets = normalizeSecrets(request.getSecrets());
        if (!submittedSecrets.isEmpty()) {
            AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
            Map<String, String> merged = existing
                    .map(this::decryptSecrets)
                    .map(LinkedHashMap::new)
                    .orElseGet(LinkedHashMap::new);
            merged.putAll(submittedSecrets);
            config.setSecretConfigCiphertext(secretCryptoService.encrypt(writeJson(merged)));
            config.setSecretMaskJson(writeJson(maskSecrets(merged)));
            config.setSecretUpdatedBy(admin.getAdminUserId());
            config.setSecretUpdatedByName(admin.getUserName());
            config.setSecretUpdatedAt(LocalDateTime.now());
        }

        if (existing.isPresent()) {
            updateById(config);
        } else {
            save(config);
        }

        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        String actionCode = existing.isPresent() ? "provider_config_update" : "provider_config_create";
        recordAudit(updated, actionCode, before, after, "success", reason(request.getReason()));
        logOperation("ai_image_provider_config_save", updated, before, after, request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO savePublicConfig(String providerCode, AdminAiImageProviderPublicConfigSaveDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        AdminAiImageProviderDTO before = toAdminDto(config);
        AiImageProviderPublicConfigDTO publicConfig = normalizePublicConfig(
                request == null ? null : request.getPublicConfig(),
                readPublicConfig(config)
        );
        String publicConfigJson = writeJson(publicConfig);
        config.setPublicConfigJson(publicConfigJson);
        updateById(config);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "public_config_update", before, after, "success", reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_public_update", updated, before, after, request == null ? null : request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO saveSecret(String providerCode, AdminAiImageProviderSecretSaveDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        Map<String, String> submitted = normalizeSecrets(request == null ? null : request.getSecrets());
        if (submitted.isEmpty()) {
            throw new BizException("请至少填写一个密钥字段");
        }
        AdminAiImageProviderDTO before = toAdminDto(config);
        Map<String, String> merged = new LinkedHashMap<>(decryptSecrets(config));
        merged.putAll(submitted);
        String secretJson = writeJson(merged);
        Map<String, String> secretMask = maskSecrets(merged);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        config.setSecretConfigCiphertext(secretCryptoService.encrypt(secretJson));
        config.setSecretMaskJson(writeJson(secretMask));
        config.setSecretUpdatedBy(admin.getAdminUserId());
        config.setSecretUpdatedByName(admin.getUserName());
        config.setSecretUpdatedAt(LocalDateTime.now());
        updateById(config);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "secret_update", before, after, "success", reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_secret_update", updated, before, after, request == null ? null : request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO clearSecret(String providerCode, AdminAiImageProviderActionDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        requireConfirm(providerCode, request);
        AdminAiImageProviderDTO before = toAdminDto(config);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        LambdaUpdateWrapper<AiImageProviderConfig> update = new LambdaUpdateWrapper<AiImageProviderConfig>()
                .eq(AiImageProviderConfig::getConfigId, config.getConfigId())
                .set(AiImageProviderConfig::getSecretConfigCiphertext, null)
                .set(AiImageProviderConfig::getSecretMaskJson, null)
                .set(AiImageProviderConfig::getSecretUpdatedBy, admin.getAdminUserId())
                .set(AiImageProviderConfig::getSecretUpdatedByName, admin.getUserName())
                .set(AiImageProviderConfig::getSecretUpdatedAt, LocalDateTime.now());
        if (Integer.valueOf(1).equals(config.getActive())) {
            update.set(AiImageProviderConfig::getActive, 0);
        }
        update(update);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "secret_clear", before, after, "success", reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_secret_clear", updated, before, after, request == null ? null : request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO enable(String providerCode, AdminAiImageProviderActionDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        AdminAiImageProviderDTO before = toAdminDto(config);
        config.setEnabled(1);
        updateById(config);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "enable", before, after, "success", reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_enable", updated, before, after, request == null ? null : request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO disable(String providerCode, AdminAiImageProviderActionDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        AdminAiImageProviderDTO before = toAdminDto(config);
        config.setEnabled(0);
        config.setActive(0);
        updateById(config);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "disable", before, after, "success", reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_disable", updated, before, after, request == null ? null : request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderDTO activate(String providerCode, AdminAiImageProviderActionDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        validateCanActivate(config);
        List<AdminAiImageProviderDTO> beforeList = adminList();
        update(new LambdaUpdateWrapper<AiImageProviderConfig>()
                .eq(AiImageProviderConfig::getDeleted, 0)
                .set(AiImageProviderConfig::getActive, 0));
        AiImageProviderConfig update = new AiImageProviderConfig();
        update.setConfigId(config.getConfigId());
        update.setEnabled(1);
        update.setActive(1);
        updateById(update);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "activate", toAdminDto(config), after, "success", reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_activate", updated, beforeList, after, request == null ? null : request.getReason());
        return after;
    }

    @Override
    @Transactional
    public AdminAiImageProviderRevealSecretRespDTO revealSecret(String providerCode, AdminAiImageProviderActionDTO request) {
        AiImageProviderConfig config = requireConfig(providerCode);
        requireConfirm(providerCode, request);
        Map<String, String> secrets = decryptSecrets(config);
        if (secrets.isEmpty()) {
            throw new BizException("当前 provider 尚未配置密钥");
        }
        AdminAiImageProviderDTO snapshot = toAdminDto(config);
        recordAudit(config, "secret_reveal", snapshot, snapshot, "success", "受控回显密钥：" + reason(request == null ? null : request.getReason()));
        logOperation("ai_image_provider_secret_reveal", config, snapshot, snapshot, request == null ? null : request.getReason());

        AdminAiImageProviderRevealSecretRespDTO dto = new AdminAiImageProviderRevealSecretRespDTO();
        dto.setProviderCode(config.getProviderCode());
        dto.setSecrets(secrets);
        dto.setRevealedAt(LocalDateTime.now());
        return dto;
    }

    @Override
    @Transactional
    public void recordTestResult(String providerCode, String status, String message) {
        AiImageProviderConfig config = requireConfig(providerCode);
        AdminAiImageProviderDTO before = toAdminDto(config);
        config.setLastTestStatus(truncate(status, 32));
        config.setLastTestMessage(truncate(sanitizeMessage(message), 512));
        config.setLastTestAt(LocalDateTime.now());
        updateById(config);
        AiImageProviderConfig updated = requireConfig(providerCode);
        AdminAiImageProviderDTO after = toAdminDto(updated);
        recordAudit(updated, "test", before, after, status, sanitizeMessage(message));
        logOperation("ai_image_provider_test", updated, before, after, sanitizeMessage(message));
    }

    @Override
    public Optional<AiImageProviderRuntimeConfig> findRuntimeConfig(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return Optional.empty();
        }
        return findByProviderCode(providerCode).map(this::toRuntimeConfig);
    }

    @Override
    public Optional<AiImageProviderRuntimeConfig> findActiveRuntimeConfig() {
        AiImageProviderConfig active = getOne(new LambdaQueryWrapper<AiImageProviderConfig>()
                .eq(AiImageProviderConfig::getActive, 1)
                .eq(AiImageProviderConfig::getEnabled, 1)
                .orderByAsc(AiImageProviderConfig::getPriority)
                .last("limit 1"), false);
        return Optional.ofNullable(active).map(this::toRuntimeConfig);
    }

    @Override
    public String resolveActiveProviderCode(String fallbackProviderCode) {
        return findActiveRuntimeConfig()
                .map(AiImageProviderRuntimeConfig::providerCode)
                .filter(StringUtils::hasText)
                .orElse(fallbackProviderCode);
    }

    @Override
    public String resolveModelCode(String providerCode, String fallbackModelCode) {
        return findRuntimeConfig(providerCode)
                .map(config -> config.model(fallbackModelCode))
                .filter(StringUtils::hasText)
                .orElse(fallbackModelCode);
    }

    @Override
    public AiImageProviderPublicConfigDTO readPublicConfig(AiImageProviderConfig config) {
        if (config == null || !StringUtils.hasText(config.getPublicConfigJson())) {
            return new AiImageProviderPublicConfigDTO();
        }
        try {
            return objectMapper.readValue(config.getPublicConfigJson(), AiImageProviderPublicConfigDTO.class);
        } catch (Exception error) {
            throw new BizException("AI provider 公开配置 JSON 格式异常：" + config.getProviderCode());
        }
    }

    @Override
    public Map<String, String> readSecretMask(AiImageProviderConfig config) {
        if (config == null || !StringUtils.hasText(config.getSecretMaskJson())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(config.getSecretMaskJson(), STRING_MAP_TYPE);
        } catch (Exception error) {
            return Collections.emptyMap();
        }
    }

    private List<AiImageProviderConfig> listOrdered() {
        return list(new LambdaQueryWrapper<AiImageProviderConfig>()
                .orderByAsc(AiImageProviderConfig::getPriority)
                .orderByAsc(AiImageProviderConfig::getConfigId));
    }

    private Optional<AiImageProviderConfig> findByProviderCode(String providerCode) {
        String normalized = normalizeProviderCode(providerCode);
        return Optional.ofNullable(getOne(new LambdaQueryWrapper<AiImageProviderConfig>()
                .eq(AiImageProviderConfig::getProviderCode, normalized)
                .last("limit 1"), false));
    }

    private AiImageProviderConfig requireConfig(String providerCode) {
        String normalized = normalizeProviderCode(providerCode);
        return findByProviderCode(normalized)
                .orElseThrow(() -> new BizException("AI 生图 provider 配置不存在：" + normalized));
    }

    private String normalizeProviderCode(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            throw new BizException("providerCode 不能为空");
        }
        String normalized = providerCode.trim();
        if (!SUPPORTED_PROVIDER_CODES.contains(normalized)) {
            throw new BizException("不支持的 AI 生图 provider：" + normalized);
        }
        return normalized;
    }

    private String resolveDisplayName(String providerCode, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return truncate(displayName, 128);
        }
        return DEFAULT_DISPLAY_NAMES.getOrDefault(providerCode, providerCode);
    }

    private Integer resolvePriority(String providerCode, Integer priority) {
        if (priority != null && priority > 0) {
            return priority;
        }
        return DEFAULT_PRIORITIES.getOrDefault(providerCode, 100);
    }

    private AiImageProviderRuntimeConfig toRuntimeConfig(AiImageProviderConfig config) {
        return new AiImageProviderRuntimeConfig(
                config.getProviderCode(),
                config.getDisplayName(),
                Integer.valueOf(1).equals(config.getEnabled()),
                Integer.valueOf(1).equals(config.getActive()),
                readPublicConfig(config),
                decryptSecrets(config)
        );
    }

    private AdminAiImageProviderDTO toAdminDto(AiImageProviderConfig config) {
        AdminAiImageProviderDTO dto = new AdminAiImageProviderDTO();
        BeanUtils.copyProperties(config, dto);
        dto.setEnabled(Integer.valueOf(1).equals(config.getEnabled()));
        dto.setActive(Integer.valueOf(1).equals(config.getActive()));
        dto.setPublicConfig(readPublicConfig(config));
        Map<String, String> secretMask = readSecretMask(config);
        dto.setSecretMask(secretMask);
        dto.setSecretConfigured(!secretMask.isEmpty());
        dto.setRequiredSecretFields(requiredSecretFields(config.getProviderCode()));
        dto.setRequiredPublicFields(requiredPublicFields(config.getProviderCode()));
        dto.setMissingPublicFields(missingPublicFields(config, dto.getPublicConfig()));
        dto.setMissingSecretFields(missingSecretFields(config, secretMask));
        dto.setActivationReady(dto.getMissingPublicFields().isEmpty() && dto.getMissingSecretFields().isEmpty());
        return dto;
    }

    private AiImageProviderPublicConfigDTO normalizePublicConfig(AiImageProviderPublicConfigDTO submitted,
                                                                AiImageProviderPublicConfigDTO current) {
        AiImageProviderPublicConfigDTO normalized = new AiImageProviderPublicConfigDTO();
        if (current != null) {
            BeanUtils.copyProperties(current, normalized);
        }
        if (submitted != null) {
            BeanUtils.copyProperties(submitted, normalized);
        }
        if (normalized.getConnectTimeoutMs() == null || normalized.getConnectTimeoutMs() <= 0) {
            normalized.setConnectTimeoutMs(10000);
        }
        if (normalized.getReadTimeoutMs() == null || normalized.getReadTimeoutMs() <= 0) {
            normalized.setReadTimeoutMs(120000);
        }
        if (normalized.getPollIntervalMs() == null || normalized.getPollIntervalMs() <= 0) {
            normalized.setPollIntervalMs(1500);
        }
        if (normalized.getMaxPollAttempts() == null || normalized.getMaxPollAttempts() <= 0) {
            normalized.setMaxPollAttempts(240);
        }
        if (normalized.getCount() == null || normalized.getCount() <= 0) {
            normalized.setCount(1);
        }
        normalizeTextFields(normalized);
        if (StringUtils.hasText(normalized.getExtraParamsJson())) {
            validateJsonObject(normalized.getExtraParamsJson());
        }
        return normalized;
    }

    private void normalizeTextFields(AiImageProviderPublicConfigDTO config) {
        config.setEndpoint(trimToNull(config.getEndpoint()));
        config.setRegion(trimToNull(config.getRegion()));
        config.setModel(trimToNull(config.getModel()));
        config.setModelVersion(trimToNull(config.getModelVersion()));
        config.setSize(trimToNull(config.getSize()));
        config.setQuality(trimToNull(config.getQuality()));
        config.setResponseFormat(trimToNull(config.getResponseFormat()));
        config.setAuthHeader(trimToNull(config.getAuthHeader()));
        config.setResolution(trimToNull(config.getResolution()));
        config.setExtraParamsJson(trimToNull(config.getExtraParamsJson()));
    }

    private Map<String, String> decryptSecrets(AiImageProviderConfig config) {
        if (config == null || !StringUtils.hasText(config.getSecretConfigCiphertext())) {
            return Collections.emptyMap();
        }
        try {
            String secretJson = secretCryptoService.decrypt(config.getSecretConfigCiphertext());
            Map<String, String> parsed = objectMapper.readValue(secretJson, STRING_MAP_TYPE);
            return parsed.entrySet().stream()
                    .filter(entry -> StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue()))
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().trim(),
                            entry -> entry.getValue().trim(),
                            (left, right) -> right,
                            LinkedHashMap::new));
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("AI provider 密钥格式异常：" + config.getProviderCode());
        }
    }

    private Map<String, String> normalizeSecrets(Map<String, String> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        secrets.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                normalized.put(key.trim(), value.trim());
            }
        });
        return normalized;
    }

    private Map<String, String> maskSecrets(Map<String, String> secrets) {
        Map<String, String> masked = new LinkedHashMap<>();
        secrets.forEach((key, value) -> {
            if (StringUtils.hasText(value)) {
                String normalized = value.trim();
                int tailLength = Math.min(4, normalized.length());
                masked.put(key, "****" + normalized.substring(normalized.length() - tailLength));
            }
        });
        return masked;
    }

    private void validateCanActivate(AiImageProviderConfig config) {
        AiImageProviderPublicConfigDTO publicConfig = readPublicConfig(config);
        List<String> missing = new ArrayList<>();
        missing.addAll(missingPublicFields(config, publicConfig));
        Map<String, String> secrets = decryptSecrets(config);
        missing.addAll(missingSecretFields(config, secrets));
        if (!missing.isEmpty()) {
            throw new BizException("激活 provider 前请补齐配置：" + String.join(", ", missing));
        }
    }

    private List<String> missingPublicFields(AiImageProviderConfig config, AiImageProviderPublicConfigDTO publicConfig) {
        List<String> missing = new ArrayList<>();
        for (String field : requiredPublicFields(config.getProviderCode())) {
            Object value = switch (field) {
                case "endpoint" -> publicConfig.getEndpoint();
                case "region" -> publicConfig.getRegion();
                case "model" -> publicConfig.getModel();
                default -> null;
            };
            if (value == null || (value instanceof String text && !StringUtils.hasText(text))) {
                missing.add(field);
            }
        }
        return missing;
    }

    private List<String> missingSecretFields(AiImageProviderConfig config, Map<String, String> secretsOrMask) {
        List<String> missing = new ArrayList<>();
        for (String field : requiredSecretFields(config.getProviderCode())) {
            if (!StringUtils.hasText(secretsOrMask.get(field))) {
                missing.add(field);
            }
        }
        return missing;
    }

    private List<String> requiredSecretFields(String providerCode) {
        return REQUIRED_SECRET_FIELDS.getOrDefault(providerCode, Collections.emptyList());
    }

    private List<String> requiredPublicFields(String providerCode) {
        return REQUIRED_PUBLIC_FIELDS.getOrDefault(providerCode, List.of("endpoint", "model"));
    }

    private void requireConfirm(String providerCode, AdminAiImageProviderActionDTO request) {
        String confirmText = request == null ? null : request.getConfirmText();
        if (!providerCode.equals(confirmText == null ? null : confirmText.trim())) {
            throw new BizException("请二次确认并输入 providerCode：" + providerCode);
        }
    }

    private void recordAudit(AiImageProviderConfig config,
                             String actionCode,
                             AdminAiImageProviderDTO before,
                             AdminAiImageProviderDTO after,
                             String result,
                             String message) {
        AdminAuthenticatedUser admin = adminAuthContext.getCurrentAdmin();
        AiImageProviderConfigAudit audit = new AiImageProviderConfigAudit();
        audit.setConfigId(config.getConfigId());
        audit.setProviderCode(config.getProviderCode());
        audit.setActionCode(actionCode);
        audit.setBeforePublicConfigJson(before == null || before.getPublicConfig() == null ? null : writeJson(before.getPublicConfig()));
        audit.setAfterPublicConfigJson(after == null || after.getPublicConfig() == null ? null : writeJson(after.getPublicConfig()));
        audit.setBeforeSecretMaskJson(before == null || before.getSecretMask() == null ? null : writeJson(before.getSecretMask()));
        audit.setAfterSecretMaskJson(after == null || after.getSecretMask() == null ? null : writeJson(after.getSecretMask()));
        audit.setOperatorId(admin == null ? 0L : admin.getAdminUserId());
        audit.setOperatorName(admin == null ? "system" : admin.getUserName());
        audit.setResultStatus(truncate(result, 32));
        audit.setMessage(truncate(sanitizeMessage(message), 512));
        auditMapper.insert(audit);
    }

    private void logOperation(String operationCode, AiImageProviderConfig config, Object before, Object after, String reason) {
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode(operationCode)
                .targetType("ai_image_provider_config")
                .targetId(config.getConfigId())
                .beforeSnapshot(before)
                .afterSnapshot(after)
                .extraContext(Map.of(
                        "provider_code", config.getProviderCode(),
                        "reason", reason(reason)
                ))
                .operationResult(1)
                .build());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new BizException("AI provider 配置序列化失败");
        }
    }

    private void validateJsonObject(String value) {
        try {
            if (!objectMapper.readTree(value).isObject()) {
                throw new BizException("扩展参数必须是 JSON Object");
            }
        } catch (JsonProcessingException error) {
            throw new BizException("扩展参数 JSON 格式异常");
        }
    }

    private String reason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : "";
    }

    private String sanitizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return message
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-+/=]+", "Bearer ***")
                .replaceAll("(?i)(secretId|secretKey|apiKey|authToken)\\s*[:=]\\s*[^,\\s}]+", "$1=***")
                .replaceAll("(?i)\"(secretId|secretKey|apiKey|authToken)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***\"")
                .trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
