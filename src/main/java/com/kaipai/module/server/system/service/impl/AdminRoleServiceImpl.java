package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminRoleAiGovernanceMatrixItemDTO;
import com.kaipai.module.model.system.dto.AdminRoleAiGovernanceMatrixRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleCopyDTO;
import com.kaipai.module.model.system.dto.AdminRoleQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleSaveDTO;
import com.kaipai.module.model.system.dto.AdminRoleStatusChangeDTO;
import com.kaipai.module.model.system.entity.AdminRole;
import com.kaipai.module.model.system.entity.AdminUserRole;
import com.kaipai.module.server.system.mapper.AdminRoleMapper;
import com.kaipai.module.server.system.service.AdminRoleService;
import com.kaipai.module.server.system.service.AdminUserRoleService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements AdminRoleService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final String AI_GOVERNANCE_PAGE_PERMISSION = "page.system.ai-resume-governance";
    private static final String OPERATION_LOGS_PAGE_PERMISSION = "page.system.operation-logs";
    private static final String AI_REVIEW_ACTION_PERMISSION = "action.system.ai-resume.review";
    private static final String AI_RESOLVE_ACTION_PERMISSION = "action.system.ai-resume.resolve";

    private final ObjectMapper objectMapper;
    private final AdminOperationLogger adminOperationLogger;
    private final AdminUserRoleService adminUserRoleService;

    @Override
    public PageResult<AdminRoleRespDTO> adminRoleList(AdminRoleQueryDTO query) {
        Page<AdminRole> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<AdminRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getRoleCode())) {
            wrapper.like(AdminRole::getRoleCode, query.getRoleCode().trim());
        }
        if (StringUtils.hasText(query.getRoleName())) {
            wrapper.like(AdminRole::getRoleName, query.getRoleName().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(AdminRole::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(AdminRole::getCreateTime).orderByDesc(AdminRole::getAdminRoleId);

        Page<AdminRole> result = page(page, wrapper);
        List<AdminRoleRespDTO> list = result.getRecords().stream().map(this::toResp).toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminRoleRespDTO adminRoleDetail(Long adminRoleId) {
        AdminRole role = getById(adminRoleId);
        if (role == null) {
            throw new BizException("后台角色不存在");
        }
        return toResp(role);
    }

    @Override
    public AdminRoleAiGovernanceMatrixRespDTO aiGovernanceMatrix() {
        List<AdminRole> roles = list(new LambdaQueryWrapper<AdminRole>()
                .orderByDesc(AdminRole::getStatus)
                .orderByAsc(AdminRole::getRoleCode)
                .orderByAsc(AdminRole::getAdminRoleId));
        Map<Long, Long> boundUserCountMap = adminUserRoleService.list().stream()
                .collect(Collectors.groupingBy(AdminUserRole::getAdminRoleId,
                        Collectors.mapping(AdminUserRole::getAdminUserId,
                                Collectors.collectingAndThen(Collectors.toSet(), userIds -> (long) userIds.size()))));

        List<AdminRoleAiGovernanceMatrixItemDTO> items = roles.stream()
                .map(role -> toAiGovernanceMatrixItem(role, boundUserCountMap.getOrDefault(role.getAdminRoleId(), 0L)))
                .sorted(Comparator.comparingInt(this::aiGovernanceStageRank)
                        .thenComparing(AdminRoleAiGovernanceMatrixItemDTO::getBoundUserCount, Comparator.reverseOrder())
                        .thenComparing(AdminRoleAiGovernanceMatrixItemDTO::getRoleCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        long enabledRoleCount = items.stream().filter(this::isEnabledRole).count();
        long aiReadyRoleCount = items.stream()
                .filter(item -> isEnabledRole(item) && Boolean.TRUE.equals(item.getAiReady()))
                .count();
        long fallbackRoleCount = items.stream()
                .filter(item -> isEnabledRole(item) && Boolean.TRUE.equals(item.getReliesOnFallback()))
                .count();
        long pendingRoleCount = items.stream()
                .filter(item -> isEnabledRole(item)
                        && !Boolean.TRUE.equals(item.getAiReady())
                        && !Boolean.TRUE.equals(item.getReliesOnFallback()))
                .count();
        long fallbackBoundUserCount = items.stream()
                .filter(item -> isEnabledRole(item) && Boolean.TRUE.equals(item.getReliesOnFallback()))
                .mapToLong(item -> item.getBoundUserCount() == null ? 0L : item.getBoundUserCount())
                .sum();

        AdminRoleAiGovernanceMatrixRespDTO dto = new AdminRoleAiGovernanceMatrixRespDTO();
        dto.setTotalRoleCount(items.size());
        dto.setEnabledRoleCount((int) enabledRoleCount);
        dto.setAiReadyRoleCount((int) aiReadyRoleCount);
        dto.setFallbackRoleCount((int) fallbackRoleCount);
        dto.setPendingRoleCount((int) pendingRoleCount);
        dto.setFallbackBoundUserCount(fallbackBoundUserCount);
        dto.setCanRetireFallback(fallbackRoleCount == 0 && fallbackBoundUserCount == 0);
        dto.setList(items);
        return dto;
    }

    @Override
    @Transactional
    public AdminRoleRespDTO createRole(AdminRoleSaveDTO dto) {
        ensureRoleCodeUnique(dto.getRoleCode(), null);
        AdminRole role = new AdminRole();
        applySaveDto(role, dto);
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        save(role);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("create")
                .targetType("admin_role")
                .targetId(role.getAdminRoleId())
                .afterSnapshot(snapshot(role))
                .extraContext(snapshot(role))
                .operationResult(1)
                .build());
        return toResp(role);
    }

    @Override
    @Transactional
    public AdminRoleRespDTO updateRole(Long adminRoleId, AdminRoleSaveDTO dto) {
        AdminRole role = getById(adminRoleId);
        if (role == null) {
            throw new BizException("后台角色不存在");
        }
        ensureRoleCodeUnique(dto.getRoleCode(), adminRoleId);
        AdminRole beforeRole = copyRole(role);
        applySaveDto(role, dto);
        updateById(role);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("edit")
                .targetType("admin_role")
                .targetId(role.getAdminRoleId())
                .beforeSnapshot(snapshot(beforeRole))
                .afterSnapshot(snapshot(role))
                .extraContext(snapshot(role))
                .operationResult(1)
                .build());
        return toResp(getById(adminRoleId));
    }

    @Override
    @Transactional
    public AdminRoleRespDTO changeRoleStatus(AdminRoleStatusChangeDTO dto) {
        AdminRole role = getById(dto.getAdminRoleId());
        if (role == null) {
            throw new BizException("后台角色不存在");
        }
        Integer beforeStatus = role.getStatus();
        Integer targetStatus = dto.getStatus();
        if (targetStatus == null || !List.of(1, 2).contains(targetStatus)) {
            throw new BizException("角色状态不合法");
        }
        if (beforeStatus != null && beforeStatus.equals(targetStatus)) {
            throw new BizException("角色当前状态已是目标状态");
        }
        AdminRole beforeRole = copyRole(role);
        role.setStatus(targetStatus);
        role.setRemark(dto.getReason());
        updateById(role);
        long boundCount = adminUserRoleService.lambdaQuery()
                .eq(AdminUserRole::getAdminRoleId, role.getAdminRoleId())
                .count();
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode(targetStatus == 1 ? "enable" : "disable")
                .targetType("admin_role")
                .targetId(role.getAdminRoleId())
                .beforeSnapshot(snapshot(beforeRole))
                .afterSnapshot(snapshot(role))
                .extraContext(buildStatusContext(role, beforeStatus, targetStatus, boundCount, dto.getReason()))
                .operationResult(1)
                .build());
        return toResp(getById(role.getAdminRoleId()));
    }

    @Override
    @Transactional
    public AdminRoleRespDTO copyRole(AdminRoleCopyDTO dto) {
        AdminRole source = getById(dto.getSourceRoleId());
        if (source == null) {
            throw new BizException("源角色不存在");
        }
        ensureRoleCodeUnique(dto.getRoleCode(), null);
        AdminRole copy = new AdminRole();
        copy.setRoleCode(dto.getRoleCode().trim());
        copy.setRoleName(dto.getRoleName().trim());
        copy.setRemark(dto.getRemark());
        copy.setStatus(1);
        copy.setMenuPermissionsJson(source.getMenuPermissionsJson());
        copy.setPagePermissionsJson(source.getPagePermissionsJson());
        copy.setActionPermissionsJson(source.getActionPermissionsJson());
        save(copy);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("copy")
                .targetType("admin_role")
                .targetId(copy.getAdminRoleId())
                .afterSnapshot(snapshot(copy))
                .extraContext(buildCopyContext(source, copy))
                .operationResult(1)
                .build());
        return toResp(copy);
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludeRoleId) {
        LambdaQueryWrapper<AdminRole> wrapper = new LambdaQueryWrapper<AdminRole>()
                .eq(AdminRole::getRoleCode, roleCode.trim());
        if (excludeRoleId != null) {
            wrapper.ne(AdminRole::getAdminRoleId, excludeRoleId);
        }
        if (count(wrapper) > 0) {
            throw new BizException("角色编码已存在");
        }
    }

    private void applySaveDto(AdminRole role, AdminRoleSaveDTO dto) {
        role.setRoleCode(dto.getRoleCode().trim());
        role.setRoleName(dto.getRoleName().trim());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        role.setMenuPermissionsJson(writePermissions(dto.getMenuPermissions()));
        role.setPagePermissionsJson(writePermissions(dto.getPagePermissions()));
        role.setActionPermissionsJson(writePermissions(dto.getActionPermissions()));
    }

    private AdminRoleRespDTO toResp(AdminRole role) {
        AdminRoleRespDTO dto = new AdminRoleRespDTO();
        BeanUtils.copyProperties(role, dto);
        dto.setMenuPermissions(readPermissions(role.getMenuPermissionsJson()));
        dto.setPagePermissions(readPermissions(role.getPagePermissionsJson()));
        dto.setActionPermissions(readPermissions(role.getActionPermissionsJson()));
        return dto;
    }

    private List<String> readPermissions(String permissionsJson) {
        if (!StringUtils.hasText(permissionsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(permissionsJson, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BizException("角色权限数据格式异常");
        }
    }

    private String writePermissions(List<String> permissions) {
        List<String> safePermissions = permissions == null ? Collections.emptyList() : permissions;
        try {
            return objectMapper.writeValueAsString(safePermissions);
        } catch (JsonProcessingException ex) {
            throw new BizException("角色权限数据序列化失败");
        }
    }

    private AdminRoleAiGovernanceMatrixItemDTO toAiGovernanceMatrixItem(AdminRole role, long boundUserCount) {
        List<String> pagePermissions = readPermissions(role.getPagePermissionsJson());
        List<String> actionPermissions = readPermissions(role.getActionPermissionsJson());
        boolean hasAiGovernancePage = pagePermissions.contains(AI_GOVERNANCE_PAGE_PERMISSION);
        boolean hasOperationLogsPage = pagePermissions.contains(OPERATION_LOGS_PAGE_PERMISSION);
        boolean hasAiReviewAction = actionPermissions.contains(AI_REVIEW_ACTION_PERMISSION);
        boolean hasAiResolveAction = actionPermissions.contains(AI_RESOLVE_ACTION_PERMISSION);
        boolean aiReady = hasAiGovernancePage && hasAiReviewAction && hasAiResolveAction;
        boolean reliesOnFallback = hasOperationLogsPage && !aiReady;
        boolean hasAnyAiPermission = hasAiGovernancePage || hasAiReviewAction || hasAiResolveAction;

        AdminRoleAiGovernanceMatrixItemDTO dto = new AdminRoleAiGovernanceMatrixItemDTO();
        dto.setAdminRoleId(role.getAdminRoleId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setStatus(role.getStatus());
        dto.setBoundUserCount(boundUserCount);
        dto.setHasAiGovernancePage(hasAiGovernancePage);
        dto.setHasOperationLogsPage(hasOperationLogsPage);
        dto.setHasAiReviewAction(hasAiReviewAction);
        dto.setHasAiResolveAction(hasAiResolveAction);
        dto.setAiReady(aiReady);
        dto.setReliesOnFallback(reliesOnFallback);
        dto.setRolloutStage(resolveAiGovernanceStage(aiReady, reliesOnFallback, hasOperationLogsPage, hasAnyAiPermission));
        dto.setMissingPermissions(buildMissingPermissions(hasAiGovernancePage, hasAiReviewAction, hasAiResolveAction));
        return dto;
    }

    private List<String> buildMissingPermissions(boolean hasAiGovernancePage, boolean hasAiReviewAction,
                                                 boolean hasAiResolveAction) {
        List<String> missingPermissions = new ArrayList<>();
        if (!hasAiGovernancePage) {
            missingPermissions.add(AI_GOVERNANCE_PAGE_PERMISSION);
        }
        if (!hasAiReviewAction) {
            missingPermissions.add(AI_REVIEW_ACTION_PERMISSION);
        }
        if (!hasAiResolveAction) {
            missingPermissions.add(AI_RESOLVE_ACTION_PERMISSION);
        }
        return missingPermissions;
    }

    private String resolveAiGovernanceStage(boolean aiReady, boolean reliesOnFallback, boolean hasOperationLogsPage,
                                            boolean hasAnyAiPermission) {
        if (aiReady) {
            return "ai_ready";
        }
        if (reliesOnFallback && hasAnyAiPermission) {
            return "compat_transition";
        }
        if (reliesOnFallback) {
            return "fallback_only";
        }
        if (hasOperationLogsPage) {
            return "fallback_only";
        }
        if (hasAnyAiPermission) {
            return "partial_ai";
        }
        return "not_granted";
    }

    private int aiGovernanceStageRank(AdminRoleAiGovernanceMatrixItemDTO item) {
        return switch (item.getRolloutStage()) {
            case "compat_transition" -> 0;
            case "fallback_only" -> 1;
            case "partial_ai" -> 2;
            case "not_granted" -> 3;
            case "ai_ready" -> 4;
            default -> 5;
        };
    }

    private boolean isEnabledRole(AdminRoleAiGovernanceMatrixItemDTO item) {
        return Integer.valueOf(1).equals(item.getStatus());
    }

    private AdminRole copyRole(AdminRole role) {
        AdminRole copy = new AdminRole();
        BeanUtils.copyProperties(role, copy);
        return copy;
    }

    private AdminRoleRespDTO snapshot(AdminRole role) {
        return toResp(role);
    }

    private Map<String, Object> buildStatusContext(AdminRole role, Integer before, Integer after,
                                                   long boundCount, String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("role_id", role.getAdminRoleId());
        context.put("role_code", role.getRoleCode());
        context.put("status_before", before);
        context.put("status_after", after);
        context.put("bound_admin_user_count", boundCount);
        context.put("reason", reason);
        context.put("menu_permissions_snapshot", role.getMenuPermissionsJson());
        context.put("page_permissions_snapshot", role.getPagePermissionsJson());
        context.put("action_permissions_snapshot", role.getActionPermissionsJson());
        return context;
    }

    private Map<String, Object> buildCopyContext(AdminRole source, AdminRole copy) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("source_role_id", source.getAdminRoleId());
        context.put("source_role_code", source.getRoleCode());
        context.put("new_role_id", copy.getAdminRoleId());
        context.put("new_role_code", copy.getRoleCode());
        context.put("menu_permissions_snapshot", copy.getMenuPermissionsJson());
        context.put("page_permissions_snapshot", copy.getPagePermissionsJson());
        context.put("action_permissions_snapshot", copy.getActionPermissionsJson());
        return context;
    }
}
