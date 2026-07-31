package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActorCardBackgroundLibraryRespDTO {

    private String style;
    private List<BackgroundItem> images;

    @Data
    public static class BackgroundItem {
        private Long id;
        private String imageUrl;
        private String thumbnailUrl;
        private Integer sortOrder;
    }
}
