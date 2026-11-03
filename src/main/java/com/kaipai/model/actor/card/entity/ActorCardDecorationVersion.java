package com.kaipai.model.actor.card.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** Insert-only decoration snapshot, independent of subsequent draft edits. */
@Data
public class ActorCardDecorationVersion {
    private Long id;
    private Long templateId;
    private Integer versionNo;
    private String schemaJson;
    private String checksum;
    private Long publishedBy;
    private String publishNote;
    private LocalDateTime publishedAt;
}
