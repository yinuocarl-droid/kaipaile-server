package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.mapper.ai.AiImageProviderConfigAuditMapper;
import com.kaipai.model.ai.entity.AiImageProviderConfig;
import com.kaipai.service.ai.AiProviderSecretCryptoService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiImageProviderTokenHubConfigTest {
    @Test void tokenHubUsesIndependentApiKeyAndNoTc3RegionRequirement() {
        var service=service("tencent-tokenhub", "{\"apiKey\":\"***\"}");
        var detail=service.adminDetail("tencent-tokenhub");
        assertEquals(List.of("apiKey"),detail.getRequiredSecretFields());
        assertEquals(List.of("endpoint","model"),detail.getRequiredPublicFields());
        assertTrue(detail.getActivationReady());
    }
    @Test void tc3SecretsDoNotSatisfyTokenHubKeyRequirement() {
        var detail=service("tencent-tokenhub","{\"secretId\":\"***\",\"secretKey\":\"***\"}").adminDetail("tencent-tokenhub");
        assertEquals(List.of("apiKey"),detail.getMissingSecretFields());assertFalse(detail.getActivationReady());
    }
    @Test void oldTencentRequirementsStayUnchanged() {
        var detail=service("tencent-hunyuan","{\"secretId\":\"***\",\"secretKey\":\"***\"}").adminDetail("tencent-hunyuan");
        assertEquals(List.of("secretId","secretKey"),detail.getRequiredSecretFields());
        assertEquals(List.of("endpoint","region","model"),detail.getRequiredPublicFields());
    }
    private AiImageProviderConfigServiceImpl service(String provider,String mask) {
        var service=spy(new AiImageProviderConfigServiceImpl(new ObjectMapper(),mock(AiProviderSecretCryptoService.class),mock(AiImageProviderConfigAuditMapper.class),mock(AdminAuthContext.class),mock(AdminOperationLogger.class)));
        var row=new AiImageProviderConfig();row.setProviderCode(provider);row.setEnabled(1);row.setActive(0);
        row.setPublicConfigJson("{\"endpoint\":\"https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v35-generation\",\"model\":\"hy-image-v3.5-preview\",\"region\":\"ap-guangzhou\"}");
        row.setSecretMaskJson(mask);
        doReturn(row).when(service).getOne(any(),eq(false));
        return service;
    }
}
