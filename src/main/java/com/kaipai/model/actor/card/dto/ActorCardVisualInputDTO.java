package com.kaipai.model.actor.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** API contracts for the independent person/background AI main-visual input. */
public final class ActorCardVisualInputDTO {
    private ActorCardVisualInputDTO() { }

    public record SaveReq(
            @NotBlank @Size(max = 32) String generationMode,
            @Positive Long personAssetId,
            @Size(max = 1024) String personImageUrl,
            @Positive Long backgroundId,
            @PositiveOrZero Long expectedRevision) { }

    public record TaskResp(
            String taskId,
            String status,
            String generatedImageUrl,
            String failureReason) { }

    public record Resp(
            Long cardId,
            String generationMode,
            String personSourceType,
            Long personAssetId,
            String personImageUrl,
            String personStableImageUrl,
            Long backgroundId,
            long revision,
            String inputFingerprint,
            String mainVisualUrl,
            TaskResp task) { }

    public record CapabilitiesResp(
            String providerCode,
            String modelCode,
            boolean personAiAvailable,
            boolean personBackgroundAiAvailable,
            String unavailableReason) { }
}
