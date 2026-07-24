package com.kaipai.model.ai.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ProfileImportApplyReqDTO {
    private String requestId;
    private String scene;
    private Long profileVersion;
    private Long workLibraryVersion;
    private Long avatarAssetId;
    private List<ConfirmedCandidate> profileCandidates = new ArrayList<>();
    private List<ConfirmedWork> works = new ArrayList<>();

    @Data
    public static class ConfirmedCandidate {
        private String candidateId;
        private String fieldKey;
        private String candidateValue;
        private String value;
        private String sourceType;
        private boolean confirmed;
        private boolean requiresExplicitConfirmation;
        private String proof;
    }

    @Data
    public static class ConfirmedWork {
        private String candidateId;
        private String sourceType;
        private boolean confirmed;
        private String proof;
        private String matchStatus;
        private String selectedAction;
        private Long matchedExperienceId;
        private List<String> allowedActions = new ArrayList<>();
        private List<String> conflictFields = new ArrayList<>();
        private WorkFields finalFields;
        private List<String> confirmedConflictFields = new ArrayList<>();
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

        public ProfileImportWorkProofValue proofValue() {
            return new ProfileImportWorkProofValue(
                    projectName, roleName, publishStatus, workTypeCode, roleLevelCode,
                    shootYear, shootMonth, platform, syncSoundStatus, collaborators,
                    achievementText, description);
        }
    }

    @Data
    public static class WorkFields {
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
    }
}
