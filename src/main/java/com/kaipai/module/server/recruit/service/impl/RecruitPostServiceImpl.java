package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.company.entity.CompanyProfile;
import com.kaipai.module.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.module.model.recruit.entity.RecruitPost;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.company.mapper.CompanyProfileMapper;
import com.kaipai.module.server.recruit.mapper.RecruitPostMapper;
import com.kaipai.module.server.recruit.service.RecruitPostService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecruitPostServiceImpl extends ServiceImpl<RecruitPostMapper, RecruitPost> implements RecruitPostService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CompanyProfileMapper companyProfileMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<RecruitRoleRespDTO> searchRoles(RecruitRoleQueryDTO query) {
        long pageNo = query.getPage() == null || query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() == null || query.getSize() <= 0 ? 20 : query.getSize();
        Page<RecruitPost> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<RecruitPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecruitPost::getPostStatus, 1)
                .and(inner -> inner.isNull(RecruitPost::getApplyDeadline).or().ge(RecruitPost::getApplyDeadline, LocalDateTime.now()));

        Integer genderCode = toGenderCode(query.getGender());
        if (genderCode != null) {
            wrapper.eq(RecruitPost::getRequireGender, genderCode);
        }
        if (query.getMinAge() != null) {
            wrapper.and(inner -> inner.isNull(RecruitPost::getRequireAgeMax).or().ge(RecruitPost::getRequireAgeMax, query.getMinAge()));
        }
        if (query.getMaxAge() != null) {
            wrapper.and(inner -> inner.isNull(RecruitPost::getRequireAgeMin).or().le(RecruitPost::getRequireAgeMin, query.getMaxAge()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(inner -> inner.like(RecruitPost::getTitle, keyword)
                    .or()
                    .like(RecruitPost::getDramaName, keyword)
                    .or()
                    .like(RecruitPost::getRoleName, keyword)
                    .or()
                    .like(RecruitPost::getRoleDesc, keyword)
                    .or()
                    .like(RecruitPost::getRequireStyleTag, keyword));
        }
        wrapper.orderByDesc(RecruitPost::getPostType)
                .orderByDesc(RecruitPost::getLastUpdate)
                .orderByDesc(RecruitPost::getRecruitPostId);

        Page<RecruitPost> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, CompanyProfile> companyMap = loadCompanyMap(result.getRecords());
        Map<Long, User> ownerMap = loadOwnerMap(result.getRecords());
        List<RecruitRoleRespDTO> list = result.getRecords().stream()
                .map(item -> toRole(item, companyMap.get(item.getCompanyProfileId()), ownerMap.get(item.getUserId())))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public RecruitRoleRespDTO detail(Long roleId) {
        RecruitPost post = getById(roleId);
        if (post == null) {
            throw new BizException("角色不存在");
        }
        CompanyProfile company = post.getCompanyProfileId() == null ? null : companyProfileMapper.selectById(post.getCompanyProfileId());
        User owner = post.getUserId() == null ? null : userMapper.selectById(post.getUserId());
        return toRole(post, company, owner);
    }

    private Map<Long, CompanyProfile> loadCompanyMap(List<RecruitPost> posts) {
        Set<Long> companyIds = posts.stream()
                .map(RecruitPost::getCompanyProfileId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (companyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return companyProfileMapper.selectBatchIds(companyIds).stream()
                .collect(Collectors.toMap(CompanyProfile::getCompanyProfileId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, User> loadOwnerMap(List<RecruitPost> posts) {
        Set<Long> userIds = posts.stream()
                .map(RecruitPost::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private RecruitRoleRespDTO toRole(RecruitPost post, CompanyProfile company, User owner) {
        RecruitRoleRespDTO dto = new RecruitRoleRespDTO();
        dto.setId(post.getRecruitPostId());
        dto.setProjectId(post.getRecruitPostId());
        dto.setRoleName(defaultText(post.getRoleName()));
        dto.setGender(toGenderText(post.getRequireGender()));
        dto.setMinAge(defaultInt(post.getRequireAgeMin(), 18));
        dto.setMaxAge(defaultInt(post.getRequireAgeMax(), 35));
        dto.setRequirement(firstNonBlank(post.getRoleDesc(), post.getTitle(), "角色要求待补充"));
        dto.setFee(resolveSalary(post));
        dto.setDeadline(formatDate(post.getApplyDeadline()));
        dto.setStatus(resolveRoleStatus(post.getPostStatus()));
        dto.setTags(splitCommaSeparated(post.getRequireStyleTag()));
        dto.setPublishTime(formatDateTime(post.getLastUpdate() != null ? post.getLastUpdate() : post.getCreateTime()));
        dto.setCoverImage(firstNonBlank(company == null ? null : company.getCoverUrl(), company == null ? null : company.getLogoUrl(), null));
        dto.setProject(buildProject(post, company, owner));
        dto.setCompany(buildCompany(post, company, owner));
        return dto;
    }

    private RecruitRoleRespDTO.ProjectDTO buildProject(RecruitPost post, CompanyProfile company, User owner) {
        RecruitRoleRespDTO.ProjectDTO project = new RecruitRoleRespDTO.ProjectDTO();
        project.setId(post.getRecruitPostId());
        project.setCompanyId(company != null && company.getUserId() != null ? company.getUserId() : post.getUserId());
        project.setTitle(firstNonBlank(post.getDramaName(), post.getTitle(), "待补充项目名称"));
        project.setDescription(firstNonBlank(post.getTitle(), company == null ? null : company.getIntro(), post.getRoleDesc()));
        project.setLocation(resolveLocation(post.getShootProvince(), post.getShootCity(), post.getShootAddress()));
        project.setStatus(resolveProjectStatus(post.getPostStatus()));
        project.setType(post.getPostType() != null && post.getPostType() == 2 ? "紧急招募" : "角色招募");
        project.setShootingDate(resolveShootingDate(post.getShootStartTime(), post.getShootEndTime()));
        project.setRoleCount(1);
        project.setCoverImage(firstNonBlank(company == null ? null : company.getCoverUrl(), company == null ? null : company.getLogoUrl(), owner == null ? null : owner.getAvatarUrl()));
        return project;
    }

    private RecruitRoleRespDTO.CompanyDTO buildCompany(RecruitPost post, CompanyProfile company, User owner) {
        RecruitRoleRespDTO.CompanyDTO dto = new RecruitRoleRespDTO.CompanyDTO();
        dto.setUserId(company != null && company.getUserId() != null ? company.getUserId() : post.getUserId());
        dto.setAvatar(firstNonBlank(company == null ? null : company.getLogoUrl(), owner == null ? null : owner.getAvatarUrl(), null));
        dto.setCompanyName(firstNonBlank(company == null ? null : company.getCompanyName(), owner == null ? null : owner.getUserName(), "精选剧组"));
        dto.setContactName(firstNonBlank(post.getContactName(), company == null ? null : company.getContactName(), null));
        dto.setContactPhone(firstNonBlank(post.getContactPhone(), company == null ? null : company.getContactPhone(), owner == null ? null : owner.getPhone()));
        dto.setRemark(firstNonBlank(company == null ? null : company.getIntro(), company == null ? null : company.getBusinessScope(), "专注影视与内容项目合作。"));
        dto.setLocation(resolveLocation(company == null ? null : company.getLocationProvince(), company == null ? null : company.getLocationCity(), company == null ? null : company.getAddress()));
        dto.setCompanyType(resolveCompanyType(company == null ? null : company.getCompanyType()));
        dto.setOfficeAddress(company == null ? null : company.getAddress());
        dto.setFocusDirection(company == null ? null : company.getBusinessScope());
        dto.setCooperationNeed(company == null ? null : company.getCooperationTag());
        return dto;
    }

    private Integer toGenderCode(String gender) {
        if (!StringUtils.hasText(gender) || "不限".equals(gender.trim())) {
            return null;
        }
        if ("男".equals(gender.trim()) || "male".equalsIgnoreCase(gender.trim())) {
            return 1;
        }
        if ("女".equals(gender.trim()) || "female".equalsIgnoreCase(gender.trim())) {
            return 2;
        }
        return null;
    }

    private String toGenderText(Integer gender) {
        if (gender != null && gender == 1) {
            return "男";
        }
        if (gender != null && gender == 2) {
            return "女";
        }
        return "不限";
    }

    private String resolveSalary(RecruitPost post) {
        if (!StringUtils.hasText(post.getSalary()) || Boolean.FALSE.equals(post.getSalaryVisible())) {
            return "面议";
        }
        return post.getSalary().trim();
    }

    private String resolveRoleStatus(Integer postStatus) {
        if (postStatus != null && postStatus == 1) {
            return "recruiting";
        }
        if (postStatus != null && postStatus == 2) {
            return "closed";
        }
        return "paused";
    }

    private Integer resolveProjectStatus(Integer postStatus) {
        return postStatus != null && postStatus == 1 ? 1 : 2;
    }

    private List<String> splitCommaSeparated(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String resolveLocation(String province, String city, String address) {
        String primary = StringUtils.hasText(city) ? city.trim() : StringUtils.hasText(province) ? province.trim() : "";
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return StringUtils.hasText(address) ? address.trim() : "地点待定";
    }

    private String resolveShootingDate(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            return "待定档期";
        }
        if (start != null && end != null) {
            String startText = start.format(DATE_FORMATTER);
            String endText = end.format(DATE_FORMATTER);
            if (startText.equals(endText)) {
                return startText;
            }
            return startText + " - " + endText;
        }
        return (start != null ? start : end).format(DATE_FORMATTER);
    }

    private String resolveCompanyType(Integer companyType) {
        if (companyType != null && companyType == 1) {
            return "传媒公司";
        }
        if (companyType != null && companyType == 2) {
            return "剧组";
        }
        if (companyType != null && companyType == 3) {
            return "选角团队";
        }
        if (companyType != null && companyType == 4) {
            return "经纪公司";
        }
        return null;
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
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
