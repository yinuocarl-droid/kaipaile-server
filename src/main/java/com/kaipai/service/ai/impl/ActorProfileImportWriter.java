package com.kaipai.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.ActorWorkInternalWriter;
import com.kaipai.service.actor.ActorWorkSourceType;
import com.kaipai.service.ai.ProfileImportWriter;
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
        if ("full_profile".equals(request.getScene())) {
            validateFullProfile(userId, request, values);
        }
        boolean created = profile == null;
        if (created) {
            profile = new ActorProfile();
            profile.setUserId(userId);
            profile.setProfileStatus(3);
            profile.setWorkLibraryVersion(0L);
            profile.setVersion(0);
            profileMapper.insert(profile);
        }
        if ("full_profile".equals(request.getScene())) {
            applyProfile(profile, request, values);
            if (profileMapper.updateById(profile) != 1) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
            }
        }
        for (ProfileImportApplyReqDTO.ConfirmedWork work : request.getWorks()) {
            workWriter.createWork(userId, toWork(work), ActorWorkSourceType.IMPORT);
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "profileCreated", created,
                    "profileUpdated", "full_profile".equals(request.getScene()),
                    "worksCreated", request.getWorks().size()));
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

    private void validateFullProfile(Long userId, ProfileImportApplyReqDTO request, Map<String, String> values) {
        for (String field : new String[] {"public_name", "gender", "age", "height", "current_city"}) {
            if (!StringUtils.hasText(values.get(field))) throw new BizException("完整档案缺少核心字段: " + field);
        }
        if (request.getAvatarAssetId() == null) throw new BizException("完整档案缺少头像");
        assetOwnershipVerifier.requireOwnedReadyPhoto(userId, request.getAvatarAssetId());
    }

    private void applyProfile(ActorProfile profile, ProfileImportApplyReqDTO request, Map<String, String> values) {
        profile.setAvatarAssetId(request.getAvatarAssetId());
        profile.setNickName(values.get("public_name").trim());
        profile.setGender("female".equals(values.get("gender")) ? 2 : 1);
        profile.setAge(parseInteger(values.get("age"), "age"));
        profile.setHeight(parseInteger(values.get("height"), "height"));
        profile.setLocationCity(values.get("current_city").trim());
        profile.setWeight(parseOptionalInteger(values.get("weight"), "weight"));
        profile.setOriginPlace(trim(values.get("origin_place")));
        profile.setSchoolName(trim(values.get("school_name")));
        profile.setMajorName(trim(values.get("major_name")));
        profile.setIntro(trim(values.get("intro")));
        profile.setBirthYear(parseOptionalInteger(values.get("birth_year"), "birth_year"));
        profile.setBirthMonth(parseOptionalInteger(values.get("birth_month"), "birth_month"));
        profile.setBirthDayOfMonth(parseOptionalInteger(values.get("birth_day"), "birth_day"));
        profile.setBirthPrecision(trim(values.get("birth_precision")));
        profile.setLanguageTagsJson(tags(values.get("language_tags")));
        profile.setSpecialtyTagsJson(tags(values.get("specialty_tags")));
        profile.setRoleTypeTagsJson(tags(values.get("role_type_tags")));
        profile.setProfessionalAbilityTagsJson(tags(values.get("professional_ability_tags")));
        profile.setProfileStatus(1);
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
