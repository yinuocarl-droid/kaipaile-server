package com.kaipai.model.actor.card.dto;

import jakarta.validation.constraints.*;

/** Public-background governance, not actors' private media assets. */
public final class AdminActorCardBackgroundDTO {
    private AdminActorCardBackgroundDTO() { }
    public record Item(Long id, String style, String imageUrl, String thumbnailUrl, int sortOrder, int enabled) { }
    public record UpdateReq(@NotBlank String style, @Min(0) @Max(99999) int sortOrder, @Min(0) @Max(1) int enabled,
                            @NotBlank String expectedStyle, @Min(0) int expectedSortOrder, @Min(0) @Max(1) int expectedEnabled) { }
    public record ContextSaveReq(@NotNull @PositiveOrZero Long expectedRevision, @Positive Long backgroundId) { }
    public record ContextResp(long revision, Long backgroundId, Item background) { }
}
