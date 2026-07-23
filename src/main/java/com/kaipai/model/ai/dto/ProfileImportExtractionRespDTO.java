package com.kaipai.model.ai.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ProfileImportExtractionRespDTO {
    private String requestId;
    private int profileCandidateCount;
    private int workCandidateCount;
    private int conflictCount;
    private int ignoredMediaPlaceholderCount;
    private List<ProfileCandidate> profileCandidates = new ArrayList<>();
    private List<WorkCandidate> workCandidates = new ArrayList<>();

    @Data
    public static class ProfileCandidate {
        private String candidateId;
        private String fieldKey;
        private String candidateValue;
        private String sourceType;
        private boolean selected;
        private boolean confirmed;
        private boolean requiresExplicitConfirmation;
        private String candidateProof;
    }

    @Data
    public static class WorkCandidate {
        private String candidateId;
        private String projectName;
        private String roleName;
        private String publishStatus;
        private String workTypeCode;
        private String roleLevelCode;
        private Integer shootYear;
        private Integer shootMonth;
        private String platform;
        private String syncSoundStatus;
        private List<String> collaborators = new ArrayList<>();
        private String achievementText;
        private String description;
        private String sourceType;
        private boolean selected;
        private String candidateProof;

        public String proofValue() {
            return String.join("|", safe(projectName), safe(roleName), safe(publishStatus), safe(workTypeCode),
                    safe(roleLevelCode), safe(shootYear), safe(shootMonth), safe(platform), safe(syncSoundStatus),
                    String.join(",", collaborators), safe(achievementText), safe(description));
        }

        private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    }
}
