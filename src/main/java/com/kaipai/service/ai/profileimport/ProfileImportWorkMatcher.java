package com.kaipai.service.ai.profileimport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImportWorkMatcher {
    private final ActorExperienceMapper experienceMapper;
    private final ProfileImportWorkMatchSupport matches;

    public void match(Long userId, List<ProfileImportExtractionRespDTO.WorkCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return;
        List<ActorExperience> works = experienceMapper.selectList(
                new LambdaQueryWrapper<ActorExperience>().and(ownedActive -> ownedActive
                        .eq(ActorExperience::getUserId, userId)
                        .eq(ActorExperience::getDeleted, 0)));
        Map<String, List<ActorExperience>> worksByIdentity = works.stream()
                .filter(work -> Objects.equals(userId, work.getUserId()))
                .filter(work -> Objects.equals(0, work.getDeleted()))
                .collect(Collectors.groupingBy(matches::identity));

        for (ProfileImportExtractionRespDTO.WorkCandidate candidate : candidates) {
            List<ActorExperience> matched = worksByIdentity.getOrDefault(matches.identity(candidate), List.of());
            if (matched.isEmpty()) {
            apply(candidate, "new", null, "create", List.of("create"), List.of());
            } else if (matched.size() > 1) {
                apply(candidate, "ambiguous", null, "skip", List.of("skip"), List.of());
            } else {
                ActorExperience target = matched.get(0);
                List<String> conflictFields = matches.conflictFields(candidate, target);
                if (conflictFields.isEmpty()) {
                    apply(candidate, "exact_match", target.getExperienceId(), "skip", List.of("skip"),
                            conflictFields, target);
                } else {
                    apply(candidate, "field_conflict", target.getExperienceId(), "skip",
                            List.of("merge", "skip"), conflictFields, target);
                }
            }
        }
    }

    private void apply(ProfileImportExtractionRespDTO.WorkCandidate candidate, String matchStatus,
            Long matchedExperienceId, String selectedAction, List<String> allowedActions,
            List<String> conflictFields) {
        apply(candidate, matchStatus, matchedExperienceId, selectedAction, allowedActions, conflictFields, null);
    }

    private void apply(ProfileImportExtractionRespDTO.WorkCandidate candidate, String matchStatus,
            Long matchedExperienceId, String selectedAction, List<String> allowedActions,
            List<String> conflictFields, ActorExperience target) {
        candidate.setMatchStatus(matchStatus);
        candidate.setMatchedExperienceId(matchedExperienceId);
        candidate.setSelectedAction(selectedAction);
        candidate.setAllowedActions(allowedActions);
        candidate.setConflictFields(conflictFields);
        candidate.setSelected("create".equals(selectedAction));
        candidate.setConflicts(new java.util.ArrayList<>());
        if (target != null) {
            for (String field : conflictFields) {
                ProfileImportExtractionRespDTO.Conflict conflict =
                        new ProfileImportExtractionRespDTO.Conflict();
                conflict.setFieldKey(field);
                conflict.setExistingValue(existingValue(target, field));
                conflict.setCandidateValue(candidateValue(candidate, field));
                ProfileImportExtractionRespDTO.FieldEvidence evidence = candidate.getFields().get(field);
                conflict.setSourceText(evidence == null ? null : evidence.getSourceText());
                candidate.getConflicts().add(conflict);
            }
        }
    }

    private Object existingValue(ActorExperience target, String field) {
        return switch (field) {
            case "projectName" -> target.getDramaName();
            case "roleName" -> target.getRoleName();
            case "publishStatus" -> target.getPublishStatus();
            case "workTypeCode" -> target.getWorkTypeCode();
            case "roleLevelCode" -> target.getRoleLevelCode();
            case "shootYear" -> target.getShootYear();
            case "shootMonth" -> target.getShootMonth();
            case "platform" -> target.getPlatform();
            case "syncSoundStatus" -> target.getSyncSoundStatus();
            case "collaborators" -> matches.collaborators(target.getCollaboratorsJson());
            case "achievementText" -> target.getAchievementText();
            case "description" -> target.getRoleDesc();
            default -> null;
        };
    }

    private Object candidateValue(ProfileImportExtractionRespDTO.WorkCandidate candidate, String field) {
        return switch (field) {
            case "projectName" -> candidate.getProjectName();
            case "roleName" -> candidate.getRoleName();
            case "publishStatus" -> candidate.getPublishStatus();
            case "workTypeCode" -> candidate.getWorkTypeCode();
            case "roleLevelCode" -> candidate.getRoleLevelCode();
            case "shootYear" -> candidate.getShootYear();
            case "shootMonth" -> candidate.getShootMonth();
            case "platform" -> candidate.getPlatform();
            case "syncSoundStatus" -> candidate.getSyncSoundStatus();
            case "collaborators" -> candidate.getCollaborators();
            case "achievementText" -> candidate.getAchievementText();
            case "description" -> candidate.getDescription();
            default -> null;
        };
    }
}
