package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.company.dto.CompanyProfileExtrasDTO;
import com.kaipai.module.model.company.entity.CompanyProfile;
import com.kaipai.module.model.recruit.dto.ProjectRespDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.module.model.recruit.dto.RoleExtraDTO;
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
    private final ObjectMapper objectMapper;

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
        Map<Long, CompanyProfile> latestCompanyByUserId = loadLatestCompanyMapByUserId(result.getRecords());
        Map<Long, User> ownerMap = loadOwnerMap(result.getRecords());
        List<RecruitRoleRespDTO> list = result.getRecords().stream()
                .map(item -> {
                    CompanyProfile company = resolveCompanyProfile(item, companyMap, latestCompanyByUserId);
                    return toRole(item, company, ownerMap.get(item.getUserId()));
                })
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public RecruitRoleRespDTO detail(Long roleId) {
        RecruitPost post = getById(roleId);
        if (post == null) {
            throw new BizException("角色不存在");
        }
        Map<Long, CompanyProfile> companyMap = new LinkedHashMap<>();
        if (post.getCompanyProfileId() != null) {
            CompanyProfile company = companyProfileMapper.selectById(post.getCompanyProfileId());
            if (company != null) {
                companyMap.put(post.getCompanyProfileId(), company);
            }
        }
        CompanyProfile company = resolveCompanyProfile(post, companyMap, loadLatestCompanyMapByUserId(List.of(post)));
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

    private Map<Long, CompanyProfile> loadLatestCompanyMapByUserId(List<RecruitPost> posts) {
        Set<Long> userIds = posts.stream()
                .map(RecruitPost::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return companyProfileMapper.selectList(new LambdaQueryWrapper<CompanyProfile>()
                        .in(CompanyProfile::getUserId, userIds)
                        .orderByDesc(CompanyProfile::getLastUpdate)
                        .orderByDesc(CompanyProfile::getCompanyProfileId))
                .stream()
                .collect(Collectors.toMap(CompanyProfile::getUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private CompanyProfile resolveCompanyProfile(RecruitPost post,
                                                 Map<Long, CompanyProfile> companyMap,
                                                 Map<Long, CompanyProfile> latestCompanyByUserId) {
        if (post == null) {
            return null;
        }
        CompanyProfile company = post.getCompanyProfileId() == null ? null : companyMap.get(post.getCompanyProfileId());
        if (company != null) {
            return company;
        }
        return post.getUserId() == null ? null : latestCompanyByUserId.get(post.getUserId());
    }

    private RecruitRoleRespDTO toRole(RecruitPost post, CompanyProfile company, User owner) {
        RoleExtraDTO roleExtra = readRoleExtra(post.getExtendedField());
        CompanyProfileExtrasDTO companyExtras = readCompanyExtras(company == null ? null : company.getExtendedField());
        ProjectRespDTO project = findProject(companyExtras, roleExtra.getProjectId());

        RecruitRoleRespDTO dto = new RecruitRoleRespDTO();
        dto.setId(post.getRecruitPostId());
        dto.setProjectId(project == null || project.getId() == null ? post.getRecruitPostId() : project.getId());
        dto.setRoleName(defaultText(post.getRoleName()));
        dto.setGender(toGenderText(post.getRequireGender()));
        dto.setMinAge(defaultInt(post.getRequireAgeMin(), 18));
        dto.setMaxAge(defaultInt(post.getRequireAgeMax(), 35));
        dto.setRequirement(firstNonBlank(post.getRoleDesc(), post.getTitle(), "角色要求待补充"));
        dto.setFee(resolveSalary(post));
        dto.setDeadline(formatDate(post.getApplyDeadline()));
        dto.setStatus(resolveRoleStatus(post.getPostStatus()));
        dto.setTags(mergeTags(roleExtra.getTags(), splitCommaSeparated(post.getRequireStyleTag())));
        dto.setPublishTime(formatDateTime(post.getLastUpdate() != null ? post.getLastUpdate() : post.getCreateTime()));
        dto.setCoverImage(firstNonBlank(roleExtra.getCoverImage(),
                project == null ? null : project.getCoverImage(),
                company == null ? null : company.getCoverUrl(),
                company == null ? null : company.getLogoUrl(),
                null));
        dto.setProject(buildProject(post, company, owner, project));
        dto.setCompany(buildCompany(post, company, owner, companyExtras));
        return dto;
    }

    private RecruitRoleRespDTO.ProjectDTO buildProject(RecruitPost post, CompanyProfile company, User owner, ProjectRespDTO projectSource) {
        RecruitRoleRespDTO.ProjectDTO project = new RecruitRoleRespDTO.ProjectDTO();
        project.setId(projectSource == null || projectSource.getId() == null ? post.getRecruitPostId() : projectSource.getId());
        project.setCompanyId(projectSource == null || projectSource.getCompanyId() == null
                ? (company != null && company.getUserId() != null ? company.getUserId() : post.getUserId())
                : projectSource.getCompanyId());
        project.setTitle(firstNonBlank(projectSource == null ? null : projectSource.getTitle(), post.getDramaName(), post.getTitle(), "待补充项目名称"));
        project.setDescription(firstNonBlank(projectSource == null ? null : projectSource.getDescription(),
                post.getTitle(),
                company == null ? null : company.getIntro(),
                post.getRoleDesc()));
        project.setLocation(firstNonBlank(projectSource == null ? null : projectSource.getLocation(),
                resolveLocation(post.getShootProvince(), post.getShootCity(), post.getShootAddress()),
                "地点待定"));
        project.setStatus(projectSource == null || projectSource.getStatus() == null
                ? resolveProjectStatus(post.getPostStatus())
                : projectSource.getStatus());
        project.setType(firstNonBlank(projectSource == null ? null : projectSource.getType(),
                post.getPostType() != null && post.getPostType() == 2 ? "紧急招募" : "角色招募"));
        project.setShootingDate(firstNonBlank(projectSource == null ? null : projectSource.getShootingDate(),
                resolveShootingDate(post.getShootStartTime(), post.getShootEndTime())));
        project.setRoleCount(projectSource != null && projectSource.getRoleCount() != null && projectSource.getRoleCount() > 0
                ? projectSource.getRoleCount()
                : 1);
        project.setCoverImage(firstNonBlank(projectSource == null ? null : projectSource.getCoverImage(),
                company == null ? null : company.getCoverUrl(),
                company == null ? null : company.getLogoUrl(),
                owner == null ? null : owner.getAvatarUrl()));
        return project;
    }

    private RecruitRoleRespDTO.CompanyDTO buildCompany(RecruitPost post,
                                                       CompanyProfile company,
                                                       User owner,
                                                       CompanyProfileExtrasDTO extras) {
        RecruitRoleRespDTO.CompanyDTO dto = new RecruitRoleRespDTO.CompanyDTO();
        dto.setUserId(company != null && company.getUserId() != null ? company.getUserId() : post.getUserId());
        dto.setAvatar(firstNonBlank(company == null ? null : company.getLogoUrl(), owner == null ? null : owner.getAvatarUrl(), null));
        dto.setCompanyName(firstNonBlank(company == null ? null : company.getCompanyName(), owner == null ? null : owner.getUserName(), "精选剧组"));
        dto.setContactName(firstNonBlank(post.getContactName(), company == null ? null : company.getContactName(), null));
        dto.setContactPhone(firstNonBlank(post.getContactPhone(), company == null ? null : company.getContactPhone(), owner == null ? null : owner.getPhone()));
        dto.setRemark(firstNonBlank(company == null ? null : company.getIntro(),
                extras == null ? null : extras.getFocusDirection(),
                company == null ? null : company.getBusinessScope(),
                "专注影视与内容项目合作。"));
        dto.setLocation(resolveLocation(company == null ? null : company.getLocationProvince(), company == null ? null : company.getLocationCity(), company == null ? null : company.getAddress()));
        dto.setCompanyType(firstNonBlank(extras == null ? null : extras.getCompanyType(), resolveCompanyType(company == null ? null : company.getCompanyType()), null));
        dto.setTeamScale(extras == null ? null : extras.getTeamScale());
        dto.setFocusDirection(firstNonBlank(extras == null ? null : extras.getFocusDirection(), company == null ? null : company.getBusinessScope(), null));
        dto.setRepresentativeWorks(extras == null ? null : extras.getRepresentativeWorks());
        dto.setCooperationNeed(firstNonBlank(extras == null ? null : extras.getCooperationNeed(), company == null ? null : company.getCooperationTag(), null));
        dto.setOfficeAddress(firstNonBlank(extras == null ? null : extras.getOfficeAddress(), company == null ? null : company.getAddress(), null));
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

    private List<String> mergeTags(List<String> extraTags, List<String> styleTags) {
        Set<String> values = new LinkedHashSet<>();
        if (extraTags != null) {
            values.addAll(extraTags.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        if (styleTags != null) {
            values.addAll(styleTags.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        return new java.util.ArrayList<>(values);
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
            return "影视制作公司";
        }
        if (companyType != null && companyType == 2) {
            return "短剧厂牌";
        }
        if (companyType != null && companyType == 3) {
            return "广告内容团队";
        }
        if (companyType != null && companyType == 4) {
            return "MCN内容团队";
        }
        if (companyType != null && companyType == 5) {
            return "品牌影像团队";
        }
        return null;
    }

    private CompanyProfileExtrasDTO readCompanyExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new CompanyProfileExtrasDTO();
        }
        try {
            CompanyProfileExtrasDTO extras = objectMapper.readValue(raw, CompanyProfileExtrasDTO.class);
            if (extras.getProjects() == null) {
                extras.setProjects(new java.util.ArrayList<>());
            }
            return extras;
        } catch (Exception ignored) {
            return new CompanyProfileExtrasDTO();
        }
    }

    private RoleExtraDTO readRoleExtra(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new RoleExtraDTO();
        }
        try {
            RoleExtraDTO extra = objectMapper.readValue(raw, RoleExtraDTO.class);
            if (extra.getTags() == null) {
                extra.setTags(new java.util.ArrayList<>());
            }
            return extra;
        } catch (Exception ignored) {
            return new RoleExtraDTO();
        }
    }

    private ProjectRespDTO findProject(CompanyProfileExtrasDTO extras, Long projectId) {
        if (extras == null || extras.getProjects() == null || projectId == null) {
            return null;
        }
        return extras.getProjects().stream()
                .filter(java.util.Objects::nonNull)
                .filter(item -> java.util.Objects.equals(item.getId(), projectId))
                .findFirst()
                .map(this::copyProject)
                .orElse(null);
    }

    private ProjectRespDTO copyProject(ProjectRespDTO source) {
        ProjectRespDTO target = new ProjectRespDTO();
        if (source == null) {
            return target;
        }
        target.setId(source.getId());
        target.setCompanyId(source.getCompanyId());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setLocation(source.getLocation());
        target.setStatus(source.getStatus());
        target.setType(source.getType());
        target.setShootingDate(source.getShootingDate());
        target.setRoleCount(source.getRoleCount());
        target.setCoverImage(source.getCoverImage());
        return target;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        if (values == null || values.length == 0) {
            return null;
        }
        return values[values.length - 1];
    }
}
