package com.kaipai.module.server.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderActionDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderPublicConfigSaveDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderRevealSecretRespDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderSaveDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderSecretSaveDTO;
import com.kaipai.module.model.ai.dto.AiImageProviderPublicConfigDTO;
import com.kaipai.module.model.ai.entity.AiImageProviderConfig;
import com.kaipai.module.server.ai.config.AiImageProviderRuntimeConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiImageProviderConfigService extends IService<AiImageProviderConfig> {

    List<AdminAiImageProviderDTO> adminList();

    AdminAiImageProviderDTO adminDetail(String providerCode);

    AdminAiImageProviderDTO saveProvider(AdminAiImageProviderSaveDTO request);

    AdminAiImageProviderDTO savePublicConfig(String providerCode, AdminAiImageProviderPublicConfigSaveDTO request);

    AdminAiImageProviderDTO saveSecret(String providerCode, AdminAiImageProviderSecretSaveDTO request);

    AdminAiImageProviderDTO clearSecret(String providerCode, AdminAiImageProviderActionDTO request);

    AdminAiImageProviderDTO enable(String providerCode, AdminAiImageProviderActionDTO request);

    AdminAiImageProviderDTO disable(String providerCode, AdminAiImageProviderActionDTO request);

    AdminAiImageProviderDTO activate(String providerCode, AdminAiImageProviderActionDTO request);

    AdminAiImageProviderRevealSecretRespDTO revealSecret(String providerCode, AdminAiImageProviderActionDTO request);

    void recordTestResult(String providerCode, String status, String message);

    Optional<AiImageProviderRuntimeConfig> findRuntimeConfig(String providerCode);

    Optional<AiImageProviderRuntimeConfig> findActiveRuntimeConfig();

    String resolveActiveProviderCode(String fallbackProviderCode);

    String resolveModelCode(String providerCode, String fallbackModelCode);

    AiImageProviderPublicConfigDTO readPublicConfig(AiImageProviderConfig config);

    Map<String, String> readSecretMask(AiImageProviderConfig config);
}
