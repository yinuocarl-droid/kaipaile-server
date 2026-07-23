package com.kaipai.model.actor.dto;

import lombok.Data;

@Data
public class ActorCareerHubSummaryRespDTO {
    private ProfileSummary profile = new ProfileSummary();
    private WorkSummary works = new WorkSummary();
    private AssetSummary assets = new AssetSummary();
    private long pendingContactRequests;

    @Data
    public static class ProfileSummary {
        private boolean coreReady;
        private int careerFieldCount;
        private String currentCity;
    }

    @Data
    public static class WorkSummary {
        private long total;
        private long representativeCount;
    }

    @Data
    public static class AssetSummary {
        private long photoCount;
        private long videoCount;
        private boolean hasCurrentResume;
    }
}
