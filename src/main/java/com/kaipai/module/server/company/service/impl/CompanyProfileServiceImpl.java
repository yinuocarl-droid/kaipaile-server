package com.kaipai.module.server.company.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.company.dto.CompanyProfileExtrasDTO;
import com.kaipai.module.model.company.dto.CompanyProfileRespDTO;
import com.kaipai.module.model.company.dto.CompanyProfileSaveDTO;
import com.kaipai.module.model.company.entity.CompanyProfile;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.company.mapper.CompanyProfileMapper;
import com.kaipai.module.server.company.service.CompanyProfileService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyProfileServiceImpl extends ServiceImpl<CompanyProfileMapper, CompanyProfile> implements CompanyProfileService {

    private static final Map<Integer, String> COMPANY_TYPE_LABELS = buildCompanyTypeLabels();

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public CompanyProfileRespDTO mineProfile(Long currentUserId) {
        return toResp(requireOrCreateProfile(currentUserId), requireUser(currentUserId));
    }

    @Override
    public CompanyProfileRespDTO profile(Long userId) {
        CompanyProfile profile = findProfile(userId);
        return toResp(profile == null ? emptyProfile(userId) : profile, requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProfile(Long currentUserId, CompanyProfileSaveDTO dto) {
        User user = requireUser(currentUserId);
        CompanyProfile profile = requireOrCreateProfile(currentUserId);
        CompanyProfileExtrasDTO extras = readExtras(profile.getExtendedField());

        profile.setCompanyName(trimToNull(dto.getCompanyName()));
        profile.setContactName(trimToNull(dto.getContactName()));
        profile.setContactPhone(trimToNull(dto.getContactPhone()));
        profile.setIntro(trimToNull(dto.getRemark()));
        profile.setLogoUrl(trimToNull(dto.getAvatar()));
        profile.setLocationCity(trimToNull(dto.getLocation()));
        profile.setAddress(trimToNull(dto.getOfficeAddress()));
        profile.setCompanyType(toCompanyTypeCode(dto.getCompanyType()));

        extras.setCompanyType(trimToNull(dto.getCompanyType()));
        extras.setTeamScale(trimToNull(dto.getTeamScale()));
        extras.setFocusDirection(trimToNull(dto.getFocusDirection()));
        extras.setRepresentativeWorks(trimToNull(dto.getRepresentativeWorks()));
        extras.setCooperationNeed(trimToNull(dto.getCooperationNeed()));
        extras.setOfficeAddress(trimToNull(dto.getOfficeAddress()));
        profile.setExtendedField(writeExtras(extras));

        updateById(profile);

        User update = new User();
        update.setUserId(currentUserId);
        update.setUserName(firstNonBlank(dto.getCompanyName(), user.getUserName(), null));
        update.setAvatarUrl(firstNonBlank(dto.getAvatar(), user.getAvatarUrl(), null));
        userMapper.updateById(update);
    }

    private CompanyProfile requireOrCreateProfile(Long userId) {
        CompanyProfile profile = findProfile(userId);
        if (profile != null) {
            return profile;
        }

        CompanyProfile created = emptyProfile(userId);
        save(created);
        return created;
    }

    private CompanyProfile findProfile(Long userId) {
        return getOne(new LambdaQueryWrapper<CompanyProfile>()
                .eq(CompanyProfile::getUserId, userId)
                .last("limit 1"), false);
    }

    private CompanyProfile emptyProfile(Long userId) {
        CompanyProfile profile = new CompanyProfile();
        profile.setUserId(userId);
        profile.setCompanyName("");
        profile.setCompanyStatus(1);
        profile.setExtendedField(writeExtras(new CompanyProfileExtrasDTO()));
        return profile;
    }

    private CompanyProfileRespDTO toResp(CompanyProfile profile, User user) {
        CompanyProfileExtrasDTO extras = readExtras(profile.getExtendedField());
        CompanyProfileRespDTO dto = new CompanyProfileRespDTO();
        dto.setUserId(profile.getUserId());
        dto.setAvatar(firstNonBlank(profile.getLogoUrl(), user == null ? null : user.getAvatarUrl(), ""));
        dto.setCompanyName(defaultText(profile.getCompanyName()));
        dto.setContactName(defaultText(profile.getContactName()));
        dto.setContactPhone(firstNonBlank(profile.getContactPhone(), user == null ? null : user.getPhone(), ""));
        dto.setRemark(defaultText(profile.getIntro()));
        dto.setLocation(firstNonBlank(profile.getLocationCity(), null, ""));
        dto.setCompanyType(firstNonBlank(extras.getCompanyType(), COMPANY_TYPE_LABELS.get(profile.getCompanyType()), ""));
        dto.setTeamScale(defaultText(extras.getTeamScale()));
        dto.setFocusDirection(defaultText(extras.getFocusDirection()));
        dto.setRepresentativeWorks(defaultText(extras.getRepresentativeWorks()));
        dto.setCooperationNeed(defaultText(extras.getCooperationNeed()));
        dto.setOfficeAddress(firstNonBlank(extras.getOfficeAddress(), profile.getAddress(), ""));
        return dto;
    }

    private CompanyProfileExtrasDTO readExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new CompanyProfileExtrasDTO();
        }
        try {
            return objectMapper.readValue(raw, CompanyProfileExtrasDTO.class);
        } catch (Exception ignored) {
            return new CompanyProfileExtrasDTO();
        }
    }

    private String writeExtras(CompanyProfileExtrasDTO extras) {
        try {
            return objectMapper.writeValueAsString(extras == null ? new CompanyProfileExtrasDTO() : extras);
        } catch (Exception e) {
            throw new BizException("公司资料序列化失败");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private Integer toCompanyTypeCode(String companyType) {
        String normalized = trimToNull(companyType);
        if (normalized == null) {
            return null;
        }
        return COMPANY_TYPE_LABELS.entrySet().stream()
                .filter(entry -> normalized.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static Map<Integer, String> buildCompanyTypeLabels() {
        Map<Integer, String> labels = new LinkedHashMap<>();
        labels.put(1, "影视制作公司");
        labels.put(2, "短剧厂牌");
        labels.put(3, "广告内容团队");
        labels.put(4, "MCN内容团队");
        labels.put(5, "品牌影像团队");
        return labels;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return fallback;
    }
}
