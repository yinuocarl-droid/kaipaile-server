package com.kaipai.service.ai.profileimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import com.kaipai.service.actor.support.ActorWorkDeduplicationSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ProfileImportWorkMatchSupport {
    private final ObjectMapper objectMapper;

    public String identity(ProfileImportExtractionRespDTO.WorkCandidate candidate) {
        return ActorWorkDeduplicationSupport.dedupeKey(candidate.getProjectName(), candidate.getRoleName());
    }

    public String identity(ProfileImportApplyReqDTO.ConfirmedWork candidate) {
        return ActorWorkDeduplicationSupport.dedupeKey(candidate.getProjectName(), candidate.getRoleName());
    }

    public String identity(ProfileImportApplyReqDTO.WorkFields candidate) {
        return ActorWorkDeduplicationSupport.dedupeKey(candidate.getProjectName(), candidate.getRoleName());
    }

    public String identity(ActorExperience work) {
        return ActorWorkDeduplicationSupport.dedupeKey(work.getDramaName(), work.getRoleName());
    }

    public List<String> conflictFields(ProfileImportExtractionRespDTO.WorkCandidate candidate,
            ActorExperience work) {
        return conflictFields(candidate.getPublishStatus(), candidate.getWorkTypeCode(), candidate.getRoleLevelCode(),
                candidate.getShootYear(), candidate.getShootMonth(), candidate.getPlatform(),
                candidate.getSyncSoundStatus(), candidate.getCollaborators(), candidate.getAchievementText(),
                candidate.getDescription(), work);
    }

    public List<String> conflictFields(ProfileImportApplyReqDTO.ConfirmedWork candidate,
            ActorExperience work) {
        return conflictFields(candidate.getPublishStatus(), candidate.getWorkTypeCode(), candidate.getRoleLevelCode(),
                candidate.getShootYear(), candidate.getShootMonth(), candidate.getPlatform(),
                candidate.getSyncSoundStatus(), candidate.getCollaborators(), candidate.getAchievementText(),
                candidate.getDescription(), work);
    }

    private List<String> conflictFields(String publishStatus, String workTypeCode, String roleLevelCode,
            Integer shootYear, Integer shootMonth, String platform, String syncSoundStatus,
            List<String> collaborators, String achievementText, String description, ActorExperience work) {
        List<String> conflicts = new ArrayList<>();
        addConflict(conflicts, "publishStatus", publishStatus, work.getPublishStatus());
        addConflict(conflicts, "workTypeCode", workTypeCode, work.getWorkTypeCode());
        addConflict(conflicts, "roleLevelCode", roleLevelCode, work.getRoleLevelCode());
        addConflict(conflicts, "shootYear", shootYear, work.getShootYear());
        addConflict(conflicts, "shootMonth", shootMonth, work.getShootMonth());
        addConflict(conflicts, "platform", platform, work.getPlatform());
        addConflict(conflicts, "syncSoundStatus", syncSoundStatus, work.getSyncSoundStatus());
        addConflict(conflicts, "collaborators", normalizeCollaborators(collaborators),
                collaborators(work.getCollaboratorsJson()));
        addConflict(conflicts, "achievementText", achievementText, work.getAchievementText());
        addConflict(conflicts, "description", description, work.getRoleDesc());
        return conflicts;
    }

    public List<String> collaborators(String collaboratorsJson) {
        if (!StringUtils.hasText(collaboratorsJson)) return List.of();
        try {
            JsonNode root = objectMapper.readTree(collaboratorsJson);
            if (root == null || !root.isArray()) return null;
            List<String> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isTextual()) return null;
                result.add(item.asText());
            }
            return result;
        } catch (Exception error) {
            return null;
        }
    }

    private List<String> normalizeCollaborators(List<String> collaborators) {
        return collaborators == null ? List.of() : collaborators;
    }

    private void addConflict(List<String> conflicts, String field, Object candidateValue, Object existingValue) {
        if (!Objects.equals(candidateValue, existingValue)) conflicts.add(field);
    }
}
