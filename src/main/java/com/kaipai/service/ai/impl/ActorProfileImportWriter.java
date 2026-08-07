package com.kaipai.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.ActorWorkInternalWriter;
import com.kaipai.service.ai.ProfileImportWriter;
import com.kaipai.service.ai.profileimport.ProfileImportBirthdayGuard;
import com.kaipai.service.ai.profileimport.ProfileImportBirthdayGuard.BirthdayTuple;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorProfileImportWriter implements ProfileImportWriter {
    private final ActorProfileMapper profileMapper;
    private final ActorWorkInternalWriter workWriter;
    private final ActorMediaAssetOwnershipVerifier assetOwnershipVerifier;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String applyImport(Long userId, ProfileImportApplyReqDTO request) {
        if (!"full_profile".equals(request.getScene()) && !"works_only".equals(request.getScene())) {
            throw new BizException("不支持的导入场景");
        }
        ActorProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId).last("limit 1"));
        assertVersions(profile, request);
        Map<String, String> values = values(request);
        BirthdayTuple birthday = null;
        if ("full_profile".equals(request.getScene())) {
            validateFullProfile(userId, profile, request, values);
            birthday = ProfileImportBirthdayGuard.normalize(profile, values);
        }
        boolean created = profile == null;
        if (created) {
            profile = new ActorProfile();
            profile.setUserId(userId);
            profile.setProfileStatus(3);
            profile.setWorkLibraryVersion(0L);
            profile.setVersion(0);
            if (profileMapper.insert(profile) != 1) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
            }
        }
        if ("full_profile".equals(request.getScene())) {
            applyProfile(profile, request, values, birthday);
            if (updateProfile(profile, birthday) != 1) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
            }
        }
        int worksCreated = 0;
        int worksSkipped = 0;
        int worksMerged = 0;
        for (ProfileImportApplyReqDTO.ConfirmedWork work : request.getWorks()) {
            String action = StringUtils.hasText(work.getSelectedAction())
                    ? work.getSelectedAction().trim() : "create";
            if ("skip".equals(action)) {
                worksSkipped++;
                continue;
            }
            if (!"create".equals(action) && !"merge".equals(action)) {
                throw new BizException("作品操作无效");
            }
            if ("merge".equals(action)) {
                if (work.getMatchedExperienceId() == null) {
                    throw new BizException("合并作品缺少目标");
                }
                workWriter.updateImportedWork(
                        userId, work.getMatchedExperienceId(), toWork(work.getFinalFields()));
                worksMerged++;
                continue;
            }
            if (work.getFinalFields() != null) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
            }
            workWriter.createImportedWork(userId, toWork(work));
            worksCreated++;
        }
        if (worksCreated + worksMerged > 0
                && profileMapper.incrementWorkLibraryVersionIfExpected(
                        profile.getActorProfileId(), request.getWorkLibraryVersion()) != 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "profileCreated", created,
                    "profileUpdated", "full_profile".equals(request.getScene()),
                    "worksCreated", worksCreated,
                    "worksSkipped", worksSkipped,
                    "worksMerged", worksMerged));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void assertVersions(ActorProfile profile, ProfileImportApplyReqDTO request) {
        long currentProfileVersion = profile == null || profile.getVersion() == null ? 0L : profile.getVersion();
        long currentWorkVersion = profile == null || profile.getWorkLibraryVersion() == null ? 0L : profile.getWorkLibraryVersion();
        if (!Objects.equals(currentProfileVersion, request.getProfileVersion())
                || !Objects.equals(currentWorkVersion, request.getWorkLibraryVersion())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
    }

    private Map<String, String> values(ProfileImportApplyReqDTO request) {
        Map<String, String> values = new LinkedHashMap<>();
        for (ProfileImportApplyReqDTO.ConfirmedCandidate candidate : request.getProfileCandidates()) {
            values.put(candidate.getFieldKey(), candidate.getValue());
        }
        return values;
    }

    private void validateFullProfile(Long userId, ActorProfile profile, ProfileImportApplyReqDTO request,
            Map<String, String> values) {
        Map<String, String> currentCore = currentCore(profile);
        for (String field : new String[] {"public_name", "gender", "age", "height", "current_city"}) {
            String effectiveValue = values.containsKey(field) ? values.get(field) : currentCore.get(field);
            if (!StringUtils.hasText(effectiveValue)) {
                throw new BizException("完整档案缺少核心字段: " + field);
            }
        }
        if (request.getAvatarAssetId() != null) {
            assetOwnershipVerifier.requireOwnedReadyPhoto(userId, request.getAvatarAssetId());
        } else if (profile == null
                || profile.getAvatarAssetId() == null && !StringUtils.hasText(profile.getAvatarUrl())) {
            throw new BizException("完整档案缺少头像");
        }
    }

    private void applyProfile(ActorProfile profile, ProfileImportApplyReqDTO request,
            Map<String, String> values, BirthdayTuple birthday) {
        if (request.getAvatarAssetId() != null) profile.setAvatarAssetId(request.getAvatarAssetId());
        if (values.containsKey("public_name")) profile.setNickName(values.get("public_name").trim());
        if (values.containsKey("gender")) profile.setGender(parseGender(values.get("gender")));
        if (values.containsKey("age")) profile.setAge(parseInteger(values.get("age"), "age"));
        if (values.containsKey("height")) profile.setHeight(parseInteger(values.get("height"), "height"));
        if (values.containsKey("current_city")) profile.setLocationCity(values.get("current_city").trim());
        if (values.containsKey("weight")) {
            profile.setWeight(parseOptionalInteger(values.get("weight"), "weight"));
        }
        if (values.containsKey("origin_place")) profile.setOriginPlace(trim(values.get("origin_place")));
        if (values.containsKey("school_name")) profile.setSchoolName(trim(values.get("school_name")));
        if (values.containsKey("major_name")) profile.setMajorName(trim(values.get("major_name")));
        if (values.containsKey("intro")) profile.setIntro(trim(values.get("intro")));
        if (birthday != null) {
            profile.setBirthYear(birthday.year());
            profile.setBirthMonth(birthday.month());
            profile.setBirthDayOfMonth(birthday.day());
            profile.setBirthPrecision(birthday.precision());
        }
        if (values.containsKey("language_tags")) {
            profile.setLanguageTagsJson(tags(values.get("language_tags")));
        }
        if (values.containsKey("specialty_tags")) {
            profile.setSpecialtyTagsJson(tags(values.get("specialty_tags")));
        }
        if (values.containsKey("role_type_tags")) {
            profile.setRoleTypeTagsJson(tags(values.get("role_type_tags")));
        }
        if (values.containsKey("professional_ability_tags")) {
            profile.setProfessionalAbilityTagsJson(tags(values.get("professional_ability_tags")));
        }
        profile.setProfileStatus(1);
    }

    private int updateProfile(ActorProfile profile, BirthdayTuple birthday) {
        if (birthday == null || "day".equals(birthday.precision())) {
            return profileMapper.updateById(profile);
        }
        LambdaUpdateWrapper<ActorProfile> update = new LambdaUpdateWrapper<>();
        update.eq(ActorProfile::getActorProfileId, profile.getActorProfileId());
        if ("year".equals(birthday.precision())) {
            update.set(ActorProfile::getBirthMonth, null);
        }
        update.set(ActorProfile::getBirthDayOfMonth, null);
        return profileMapper.update(profile, update);
    }

    private Map<String, String> currentCore(ActorProfile profile) {
        if (profile == null) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("public_name", profile.getNickName());
        values.put("gender", switch (profile.getGender() == null ? 0 : profile.getGender()) {
            case 1 -> "male";
            case 2 -> "female";
            default -> null;
        });
        values.put("age", profile.getAge() == null ? null : profile.getAge().toString());
        values.put("height", profile.getHeight() == null ? null : profile.getHeight().toString());
        values.put("current_city", profile.getLocationCity());
        return values;
    }

    private Integer parseGender(String value) {
        return switch (value) {
            case "male" -> 1;
            case "female" -> 2;
            default -> throw ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
        };
    }

    private ActorWorkSaveDTO toWork(ProfileImportApplyReqDTO.ConfirmedWork source) {
        if (!StringUtils.hasText(source.getProjectName())) throw new BizException("作品名称不能为空");
        ActorWorkSaveDTO work = new ActorWorkSaveDTO();
        work.setProjectName(source.getProjectName());
        work.setRoleName(source.getRoleName());
        work.setPublishStatus(source.getPublishStatus());
        work.setWorkTypeCode(source.getWorkTypeCode());
        work.setRoleLevelCode(source.getRoleLevelCode());
        work.setShootYear(source.getShootYear());
        work.setShootMonth(source.getShootMonth());
        work.setPlatform(source.getPlatform());
        work.setSyncSoundStatus(source.getSyncSoundStatus());
        work.setCollaborators(source.getCollaborators());
        work.setAchievementText(source.getAchievementText());
        work.setDescription(source.getDescription());
        return work;
    }

    private ActorWorkSaveDTO toWork(ProfileImportApplyReqDTO.WorkFields source) {
        if (!StringUtils.hasText(source.getProjectName())) throw new BizException("作品名称不能为空");
        ActorWorkSaveDTO work = new ActorWorkSaveDTO();
        work.setProjectName(source.getProjectName());
        work.setRoleName(source.getRoleName());
        work.setPublishStatus(source.getPublishStatus());
        work.setWorkTypeCode(source.getWorkTypeCode());
        work.setRoleLevelCode(source.getRoleLevelCode());
        work.setShootYear(source.getShootYear());
        work.setShootMonth(source.getShootMonth());
        work.setPlatform(source.getPlatform());
        work.setSyncSoundStatus(source.getSyncSoundStatus());
        work.setCollaborators(source.getCollaborators());
        work.setAchievementText(source.getAchievementText());
        work.setDescription(source.getDescription());
        return work;
    }

    private Integer parseInteger(String value, String field) {
        try { return Integer.valueOf(value); } catch (Exception error) { throw new BizException(field + " 格式错误"); }
    }

    private Integer parseOptionalInteger(String value, String field) {
        return StringUtils.hasText(value) ? parseInteger(value, field) : null;
    }

    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private String tags(String value) {
        try {
            if (!StringUtils.hasText(value)) return "[]";
            if (value.trim().startsWith("[")) {
                return objectMapper.writeValueAsString(objectMapper.readValue(value, java.util.List.class));
            }
            return objectMapper.writeValueAsString(java.util.Arrays.stream(value.split("[,，、]"))
                    .map(String::trim).filter(StringUtils::hasText).distinct().toList());
        } catch (Exception error) {
            throw new BizException("标签格式错误");
        }
    }
}
