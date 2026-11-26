package com.kaipai.model.ai.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ProfileImportExtractionRespDTO {
    private String requestId;
    private Long profileVersion;
    private Long workLibraryVersion;
    private int profileCandidateCount;
    private int workCandidateCount;
    private int conflictCount;
    private int ignoredMediaPlaceholderCount;
    private List<ProfileCandidate> profileCandidates = new ArrayList<>();
    private List<WorkCandidate> workCandidates = new ArrayList<>();
    private List<String> unmappedSegments = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class ProfileCandidate {
        private String candidateId;
        private String fieldKey;
        private String candidateValue;
        private Double confidence;
        private String sourceText;
        private String sourceType;
        private String warning;
        private String reviewStatus;
        private boolean selected;
        private boolean confirmed;
        private boolean requiresExplicitConfirmation;
        private Conflict conflict;
        private String candidateProof;
    }

    @Data
    public static class FieldEvidence {
        private Object candidateValue;
        private Double confidence;
        private String sourceText;
        private String sourceType;
        private String warning;
    }

    @Data
    public static class Conflict {
        private String fieldKey;
        private Object existingValue;
        private Object candidateValue;
        private String sourceText;
    }

    @Data
    public static class WorkCandidate {
        private String candidateId;
        private String matchStatus;
        private Long matchedExperienceId;
        private String selectedAction;
        private List<String> allowedActions = new ArrayList<>();
        private List<String> conflictFields = new ArrayList<>();
        private Map<String, FieldEvidence> fields = new LinkedHashMap<>();
        private List<Conflict> conflicts = new ArrayList<>();
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

        public ProfileImportWorkProofValue proofValue() {
            return new ProfileImportWorkProofValue(
                    projectName, roleName, publishStatus, workTypeCode, roleLevelCode,
                    shootYear, shootMonth, platform, syncSoundStatus, collaborators,
                    achievementText, description);
        }
    }
}
