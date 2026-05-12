package com.kaipai.module.controller.admin.ai;

import com.kaipai.common.result.R;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderTestReqDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderTestRespDTO;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.provider.AiProfileImageProvider;
import com.kaipai.module.server.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiImageProviderControllerTest {

    @Test
    void testShouldPersistProviderUrlBeforeReturningResponse() {
        AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
        AiGeneratedImageStorage storage = mock(AiGeneratedImageStorage.class);
        AiProfileImageProvider provider = new StaticProvider(
                "aliyun-qwen-image",
                AiProfileImageGenerationResult.imageUrl("https://provider.example/generated.png"));
        AdminAiImageProviderController controller = newController(configService, storage, provider);

        when(storage.uploadFromUrl("https://provider.example/generated.png", "ai-profile-card-test"))
                .thenReturn("https://cdn.kplyyk.com/ai-profile-card-test/generated.png");

        R<AdminAiImageProviderTestRespDTO> response = controller.test("aliyun-qwen-image", validRequest());

        assertEquals("success", response.getData().getStatus());
        assertEquals("https://cdn.kplyyk.com/ai-profile-card-test/generated.png", response.getData().getImageUrl());
        verify(storage).uploadFromUrl("https://provider.example/generated.png", "ai-profile-card-test");
        verify(configService).recordTestResult("aliyun-qwen-image", "success", "测试生成成功");
    }

    @Test
    void testShouldPersistProviderBytesBeforeReturningResponse() {
        AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
        AiGeneratedImageStorage storage = mock(AiGeneratedImageStorage.class);
        byte[] imageBytes = new byte[]{1, 2, 3};
        AiProfileImageProvider provider = new StaticProvider(
                "baidu-qianfan",
                AiProfileImageGenerationResult.imageBytes(imageBytes, null));
        AdminAiImageProviderController controller = newController(configService, storage, provider);

        when(storage.upload(imageBytes, "image/png", "ai-profile-card-test"))
                .thenReturn("https://cdn.kplyyk.com/ai-profile-card-test/generated-bytes.png");

        R<AdminAiImageProviderTestRespDTO> response = controller.test("baidu-qianfan", validRequest());

        assertEquals("success", response.getData().getStatus());
        assertEquals("https://cdn.kplyyk.com/ai-profile-card-test/generated-bytes.png", response.getData().getImageUrl());
        verify(storage).upload(imageBytes, "image/png", "ai-profile-card-test");
        verify(configService).recordTestResult("baidu-qianfan", "success", "测试生成成功");
    }

    @Test
    void testShouldRecordFailureWhenProviderReturnsNoImage() {
        AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
        AiGeneratedImageStorage storage = mock(AiGeneratedImageStorage.class);
        AiProfileImageProvider provider = new StaticProvider(
                "volc-seedream",
                new AiProfileImageGenerationResult(null, null, null));
        AdminAiImageProviderController controller = newController(configService, storage, provider);

        R<AdminAiImageProviderTestRespDTO> response = controller.test("volc-seedream", validRequest());

        assertEquals("failed", response.getData().getStatus());
        assertEquals("测试生成结果缺少图片内容", response.getData().getMessage());
        verify(configService).recordTestResult("volc-seedream", "failed", "测试生成结果缺少图片内容");
    }

    private AdminAiImageProviderController newController(AiImageProviderConfigService configService,
                                                         AiGeneratedImageStorage storage,
                                                         AiProfileImageProvider provider) {
        return new AdminAiImageProviderController(
                configService,
                new AiProfileImageProviderRegistry(List.of(provider)),
                storage);
    }

    private AdminAiImageProviderTestReqDTO validRequest() {
        AdminAiImageProviderTestReqDTO request = new AdminAiImageProviderTestReqDTO();
        request.setSourceImageUrl("https://cdn.kplyyk.com/source.png");
        request.setPrompt("test prompt");
        request.setTemplateSceneCode("classic");
        request.setStyleCode("classic");
        return request;
    }

    private record StaticProvider(String providerCode,
                                  AiProfileImageGenerationResult generationResult) implements AiProfileImageProvider {
        @Override
        public String modelCode() {
            return providerCode + "-model";
        }

        @Override
        public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
            return generationResult;
        }
    }
}
