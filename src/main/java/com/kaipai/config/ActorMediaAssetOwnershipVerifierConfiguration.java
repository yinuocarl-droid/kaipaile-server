package com.kaipai.config;

import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorMediaAssetOwnershipVerifierConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActorMediaAssetOwnershipVerifier.class)
    ActorMediaAssetOwnershipVerifier unavailableActorMediaAssetOwnershipVerifier() {
        return new ActorMediaAssetOwnershipVerifier() {
            @Override
            public void requireOwnedReadyPhoto(Long userId, Long assetId) {
                throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();
            }

            @Override
            public void requireOwnedReadyPdf(Long userId, Long assetId) {
                throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();
            }
        };
    }
}
