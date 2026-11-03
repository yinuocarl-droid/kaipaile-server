package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ActorCardExpandSpringWiringTest {

    @Test
    void springSelectsTheFullB5ConstructorsInsteadOfLookingForANoArgConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            bean(context, ActorCardMapper.class);
            bean(context, ActorAiProfileCardTaskMapper.class);
            bean(context, AiProfileImageProviderRegistry.class);
            bean(context, AiImageProviderConfigService.class);
            context.registerBean(AiProfileCardProperties.class, AiProfileCardProperties::new);
            bean(context, ActorMediaAssetService.class);
            bean(context, ActorCardMediaBindingService.class);
            bean(context, AiGeneratedImageStorage.class);
            bean(context, ActorCardVisualInputMapper.class);
            bean(context, UserMapper.class);
            context.registerBean("aiProfileCardTaskExecutor", Executor.class, () -> Runnable::run);
            context.register(ActorCardExpandResultService.class, ActorCardExpandImageService.class);

            context.refresh();

            assertNotNull(context.getBean(ActorCardExpandResultService.class));
            assertNotNull(context.getBean(ActorCardExpandImageService.class));
        }
    }

    private <T> void bean(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }
}
