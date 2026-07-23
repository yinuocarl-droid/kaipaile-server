package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ActorProfileCareerUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileCoreUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileRespDTO;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.ActorProfileWriteService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorProfileWriteServiceImpl implements ActorProfileWriteService {

    private final ActorProfileMapper profileMapper;
    private final ActorMediaAssetOwnershipVerifier assetOwnershipVerifier;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActorProfileRespDTO saveMine(Long currentUserId, ActorProfileMineUpdateDTO request) {
        ActorProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, currentUserId)
                .last("limit 1"));
        if (profile == null || !request.getExpectedProfileVersion().equals(profile.getVersion())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }

        assetOwnershipVerifier.requireOwnedReadyPhoto(currentUserId, request.getAvatarAssetId());
        applyCore(profile, request.getCore());
        applyCareer(profile, request.getCareer());
        profile.setAvatarAssetId(request.getAvatarAssetId());
        profile.setIntro(trimToNull(request.getIntro()));

        Integer previousVersion = profile.getVersion();
        if (profileMapper.updateById(profile) != 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
        if (Objects.equals(previousVersion, profile.getVersion())) {
            profile.setVersion(previousVersion + 1);
        }
        return toResponse(profile);
    }

    private void applyCore(ActorProfile profile, ActorProfileCoreUpdateDTO core) {
        profile.setNickName(core.getPublicName().trim());
        profile.setGender("female".equals(core.getGender()) ? 2 : 1);
        profile.setAge(core.getAge());
        profile.setHeight(core.getHeight());
        profile.setLocationCity(core.getCurrentCity().trim());
    }

    private void applyCareer(ActorProfile profile, ActorProfileCareerUpdateDTO career) {
        profile.setWeight(career.getWeight());
        profile.setOriginPlace(trimToNull(career.getOriginPlace()));
        profile.setSchoolName(trimToNull(career.getSchoolName()));
        profile.setMajorName(trimToNull(career.getMajorName()));
        profile.setLanguageTagsJson(writeTags(career.getLanguageTags()));
        profile.setSpecialtyTagsJson(writeTags(career.getSpecialtyTags()));
        profile.setRoleTypeTagsJson(writeTags(career.getRoleTypeTags()));
        profile.setProfessionalAbilityTagsJson(writeTags(career.getProfessionalAbilityTags()));
    }

    private ActorProfileRespDTO toResponse(ActorProfile profile) {
        ActorProfileRespDTO response = new ActorProfileRespDTO();
        response.setActorProfileId(profile.getActorProfileId());
        response.setUserId(profile.getUserId());
        response.setProfileVersion(profile.getVersion());
        response.setWorkLibraryVersion(profile.getWorkLibraryVersion() == null ? 0L : profile.getWorkLibraryVersion());
        response.setAvatarAssetId(profile.getAvatarAssetId());
        response.setPublicName(profile.getNickName());
        response.setGender(profile.getGender() != null && profile.getGender() == 2 ? "female" : "male");
        response.setAge(profile.getAge());
        response.setHeight(profile.getHeight());
        response.setCurrentCity(profile.getLocationCity());
        response.setWeight(profile.getWeight());
        response.setOriginPlace(profile.getOriginPlace());
        response.setSchoolName(profile.getSchoolName());
        response.setMajorName(profile.getMajorName());
        response.setLanguageTags(readTags(profile.getLanguageTagsJson()));
        response.setSpecialtyTags(readTags(profile.getSpecialtyTagsJson()));
        response.setRoleTypeTags(readTags(profile.getRoleTypeTagsJson()));
        response.setProfessionalAbilityTags(readTags(profile.getProfessionalAbilityTagsJson()));
        response.setIntro(profile.getIntro());
        return response;
    }

    private String writeTags(List<String> tags) {
        try {
            List<String> normalized = tags == null ? List.of() : tags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception error) {
            throw new IllegalStateException("profile career tags serialization failed", error);
        }
    }

    private List<String> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("profile career tags deserialization failed", error);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
