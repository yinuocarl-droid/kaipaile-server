package com.kaipai.service.ai.profileimport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImportWorkApplyGuard {
    private static final Set<String> CONFLICT_FIELDS = Set.of(
            "publishStatus", "workTypeCode", "roleLevelCode", "shootYear", "shootMonth", "platform",
            "syncSoundStatus", "collaborators", "achievementText", "description");

    private final ActorExperienceMapper experienceMapper;
    private final ActorProfileMapper profileMapper;
    private final ProfileImportWorkMatchSupport matches;

    public void validateAndLock(Long userId, List<ProfileImportApplyReqDTO.ConfirmedWork> works) {
        if (works == null) throw conflict();
        ActorProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId).eq(ActorProfile::getDeleted, 0).last("limit 1"));
        Set<Long> targetIds = new LinkedHashSet<>();
        Set<String> candidateIdentities = new HashSet<>();
        for (ProfileImportApplyReqDTO.ConfirmedWork work : works) {
            validateCollections(work);
            if (!candidateIdentities.add(matches.identity(work))) throw conflict();
            if (work.getMatchedExperienceId() != null && !targetIds.add(work.getMatchedExperienceId())) {
                throw conflict();
            }
        }
        Map<Long, ActorExperience> targets = new HashMap<>();
        targetIds.stream().sorted().forEach(id -> {
            ActorExperience target = experienceMapper.selectOwnedActiveByIdForUpdate(userId, id);
            if (target == null || !Objects.equals(userId, target.getUserId())
                    || !Objects.equals(0, target.getDeleted()) || profile == null
                    || !Objects.equals(profile.getActorProfileId(), target.getActorProfileId())) throw conflict();
            targets.put(id, target);
        });
        for (ProfileImportApplyReqDTO.ConfirmedWork work : works) {
            validate(work, targets.get(work.getMatchedExperienceId()));
        }
    }

    private void validate(ProfileImportApplyReqDTO.ConfirmedWork work, ActorExperience target) {
        if ("new".equals(work.getMatchStatus())) {
            if (work.getFinalFields() != null || target != null) throw conflict();
            return;
        }
        if ("ambiguous".equals(work.getMatchStatus())) {
            if (target != null || work.getFinalFields() != null) throw conflict();
            return;
        }
        if (target == null || !Objects.equals(matches.identity(work), matches.identity(target))) throw conflict();
        List<String> currentConflicts = matches.conflictFields(work, target);
        if (!Objects.equals(currentConflicts, work.getConflictFields())) throw conflict();
        if ("exact_match".equals(work.getMatchStatus())) {
            if (!currentConflicts.isEmpty() || work.getFinalFields() != null) throw conflict();
            return;
        }
        if (!"field_conflict".equals(work.getMatchStatus())) throw conflict();
        if ("skip".equals(work.getSelectedAction())) {
            if (work.getFinalFields() != null) throw conflict();
            return;
        }
        validateMerge(work, target);
    }

    private void validateMerge(ProfileImportApplyReqDTO.ConfirmedWork work, ActorExperience target) {
        ProfileImportApplyReqDTO.WorkFields selected = work.getFinalFields();
        if (selected == null
                || !candidateOrCurrent(
                        selected.getProjectName(), work.getProjectName(), target.getDramaName())
                || !candidateOrCurrent(
                        selected.getRoleName(), work.getRoleName(), target.getRoleName())
                || !Objects.equals(matches.identity(selected), matches.identity(target))) throw conflict();
        Map<String, Object> candidate = values(work);
        Map<String, Object> current = values(target);
        Map<String, Object> finals = values(selected);
        for (String field : candidate.keySet()) {
            Object finalValue = finals.get(field);
            if (work.getConflictFields().contains(field)) {
                if (!Objects.equals(finalValue, candidate.get(field))
                        && !Objects.equals(finalValue, current.get(field))) throw conflict();
            } else if (!Objects.equals(finalValue, current.get(field))) {
                throw conflict();
            }
        }
    }

    private void validateCollections(ProfileImportApplyReqDTO.ConfirmedWork work) {
        if (work == null || invalid(work.getCollaborators()) || work.getAllowedActions() == null
                || work.getConflictFields() == null || work.getConfirmedConflictFields() == null
                || work.getAllowedActions().size() > 2 || work.getConflictFields().size() > CONFLICT_FIELDS.size()
                || work.getConfirmedConflictFields().size() > CONFLICT_FIELDS.size()
                || !CONFLICT_FIELDS.containsAll(work.getConflictFields())) throw conflict();
        if (work.getFinalFields() != null && invalid(work.getFinalFields().getCollaborators())) throw conflict();
    }

    private boolean invalid(List<String> values) {
        return values == null || values.size() > 50 || values.stream().anyMatch(Objects::isNull);
    }

    private boolean candidateOrCurrent(Object selected, Object candidate, Object current) {
        return Objects.equals(selected, candidate) || Objects.equals(selected, current);
    }

    private Map<String, Object> values(ProfileImportApplyReqDTO.ConfirmedWork work) {
        return values(work.getPublishStatus(), work.getWorkTypeCode(), work.getRoleLevelCode(), work.getShootYear(),
                work.getShootMonth(), work.getPlatform(), work.getSyncSoundStatus(), work.getCollaborators(),
                work.getAchievementText(), work.getDescription());
    }

    private Map<String, Object> values(ProfileImportApplyReqDTO.WorkFields work) {
        return values(work.getPublishStatus(), work.getWorkTypeCode(), work.getRoleLevelCode(), work.getShootYear(),
                work.getShootMonth(), work.getPlatform(), work.getSyncSoundStatus(), work.getCollaborators(),
                work.getAchievementText(), work.getDescription());
    }

    private Map<String, Object> values(ActorExperience work) {
        return values(work.getPublishStatus(), work.getWorkTypeCode(), work.getRoleLevelCode(), work.getShootYear(),
                work.getShootMonth(), work.getPlatform(), work.getSyncSoundStatus(),
                matches.collaborators(work.getCollaboratorsJson()), work.getAchievementText(), work.getRoleDesc());
    }

    private Map<String, Object> values(String publishStatus, String workTypeCode, String roleLevelCode,
            Integer shootYear, Integer shootMonth, String platform, String syncSoundStatus,
            List<String> collaborators, String achievementText, String description) {
        Map<String, Object> values = new HashMap<>();
        values.put("publishStatus", publishStatus);
        values.put("workTypeCode", workTypeCode);
        values.put("roleLevelCode", roleLevelCode);
        values.put("shootYear", shootYear);
        values.put("shootMonth", shootMonth);
        values.put("platform", platform);
        values.put("syncSoundStatus", syncSoundStatus);
        values.put("collaborators", collaborators);
        values.put("achievementText", achievementText);
        values.put("description", description);
        return values;
    }

    private RuntimeException conflict() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
    }
}
