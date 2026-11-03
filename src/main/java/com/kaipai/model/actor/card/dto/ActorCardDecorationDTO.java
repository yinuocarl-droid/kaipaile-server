package com.kaipai.model.actor.card.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Admin decoration API requests and views; persistence entities are never exposed. */
public final class ActorCardDecorationDTO {
    private ActorCardDecorationDTO() { }

    public record SaveReq(@NotNull @PositiveOrZero Long expectedRevision, @NotNull JsonNode config) { }
    public record PreviewReq(@NotNull JsonNode config) { }
    public record ChangeReq(@NotNull @PositiveOrZero Long expectedRevision,
                            @NotBlank @Size(max = 200) String note) { }
    public record DraftResp(Long templateId, long revision, Integer activeVersion,
                            JsonNode config, JsonNode preview) { }
    public record VersionResp(Long versionId, Long templateId, int versionNo, String checksum,
                              String note, Long publishedBy, LocalDateTime publishedAt, JsonNode config) { }
    public record VersionItem(Long versionId, int versionNo, String checksum, String note,
                              Long publishedBy, LocalDateTime publishedAt) { }
}
