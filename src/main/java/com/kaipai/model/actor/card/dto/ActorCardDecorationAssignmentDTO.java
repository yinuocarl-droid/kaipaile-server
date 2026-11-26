package com.kaipai.model.actor.card.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** API contracts for decoration binding, locked assignment and owner upgrade. */
public final class ActorCardDecorationAssignmentDTO {
    private ActorCardDecorationAssignmentDTO() { }

    public record DecorationEnvelope(Long versionId, Long templateId, int templateVersion,
                                     int schemaVersion, JsonNode config) { }

    public record BindingSaveReq(@NotNull @PositiveOrZero Long expectedRevision,
                                 @NotNull @Positive Long templateId,
                                 @NotNull @Positive Long versionId,
                                 @NotBlank @Size(max = 200) String note) { }

    public record BindingResp(String sourceType, Long sourceId, long revision, Long templateId,
                              Long versionId, Integer templateVersion, LocalDateTime updatedAt) { }

    public record UpgradePreviewResp(Long expectedVersionId, boolean upgradeAvailable,
                                     DecorationEnvelope current, DecorationEnvelope target) { }

    public record UpgradeReq(Long expectedVersionId, @NotNull @Positive Long targetVersionId) { }
}
