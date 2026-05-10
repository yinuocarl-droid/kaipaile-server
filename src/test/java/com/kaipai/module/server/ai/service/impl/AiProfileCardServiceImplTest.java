package com.kaipai.module.server.ai.service.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProfileCardServiceImplTest {

    @Test
    void resolveGeneratedImageUrlShouldRejectSourceImageEcho() {
        AiProfileCardServiceImpl service = new AiProfileCardServiceImpl(null, null, null, null, null, null, null);
        AiProfileImageGenerationResult result = AiProfileImageGenerationResult.imageUrl(
                "https://cdn.kplyyk.com/source.png?token=generated");

        BizException error = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(
                service,
                "resolveGeneratedImageUrl",
                result,
                "https://cdn.kplyyk.com/source.png?token=source"));

        assertEquals("AI 图片生成结果不能直接返回原始参考图", error.getMessage());
    }
}
