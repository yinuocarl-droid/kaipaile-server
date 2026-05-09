package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.recruit.dto.RecruitApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitApplyRespDTO;
import com.kaipai.module.model.recruit.entity.RecruitApply;
import com.kaipai.module.model.recruit.entity.RecruitPost;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.service.ActorProfileService;
import com.kaipai.module.server.recruit.service.RecruitPostService;
import com.kaipai.module.server.recruit.mapper.RecruitApplyMapper;
import com.kaipai.module.server.recruit.service.RecruitApplyService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecruitApplyServiceImpl extends ServiceImpl<RecruitApplyMapper, RecruitApply> implements RecruitApplyService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int USER_TYPE_ACTOR = 1;
    private static final int USER_TYPE_CREW = 2;

    private static final int DB_STATUS_PENDING = 1;
    private static final int DB_STATUS_VIEWED = 2;
    private static final int DB_STATUS_APPROVED = 4;
    private static final int DB_STATUS_REJECTED = 5;
    private static final int DB_STATUS_CANCELLED = 6;

    private final RecruitPostService recruitPostService;
    private final ActorProfileService actorProfileService;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecruitApplyRespDTO submit(Long actorUserId, Long roleId, String remark) {
        User actor = requireUser(actorUserId);
        ensureUserType(actor, USER_TYPE_ACTOR, "只有演员可以投递角色");
        RecruitPost post = requireOpenRole(roleId);
        boolean duplicated = lambdaQuery()
                .eq(RecruitApply::getRecruitPostId, roleId)
                .eq(RecruitApply::getActorUserId, actorUserId)
                .ne(RecruitApply::getApplyStatus, DB_STATUS_CANCELLED)
                .count() > 0;
        if (duplicated) {
            throw new BizException("你已经投递过这个角色");
        }

        RecruitApply apply = new RecruitApply();
        apply.setRecruitPostId(roleId);
        apply.setActorUserId(actorUserId);
        apply.setActorProfileId(loadActorProfileId(actorUserId));
        apply.setApplyMessage(trimToNull(remark));
        apply.setApplyStatus(DB_STATUS_PENDING);
        save(apply);

        post.setApplyCount(safeCount(post.getApplyCount()) + 1);
        recruitPostService.updateById(post);
        return detail(actorUserId, apply.getRecruitApplyId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long actorUserId, Long applyId) {
        RecruitApply apply = requireApply(applyId);
        if (!actorUserId.equals(apply.getActorUserId())) {
            throw new BizException("只能取消自己的投递记录");
        }
        if (apply.getApplyStatus() != null && apply.getApplyStatus() != DB_STATUS_PENDING && apply.getApplyStatus() != DB_STATUS_VIEWED) {
            throw new BizException("当前投递状态不支持取消");
        }
        RecruitApply update = new RecruitApply();
        update.setRecruitApplyId(applyId);
        update.setApplyStatus(DB_STATUS_CANCELLED);
        updateById(update);
    }

    @Override
    public PageResult<RecruitApplyRespDTO> myApplies(Long actorUserId, RecruitApplyQueryDTO query) {
        requireUser(actorUserId);
        long pageNo = query.getPage() == null || query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() == null || query.getSize() <= 0 ? 20 : query.getSize();
        Page<RecruitApply> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<RecruitApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecruitApply::getActorUserId, actorUserId);
        applyStatusFilter(wrapper, query.getStatus());
        if (query.getRoleId() != null) {
            wrapper.eq(RecruitApply::getRecruitPostId, query.getRoleId());
        }
        wrapper.orderByDesc(RecruitApply::getCreateTime)
                .orderByDesc(RecruitApply::getRecruitApplyId);

        Page<RecruitApply> result = page(page, wrapper);
        List<RecruitApplyRespDTO> list = result.getRecords().stream()
                .map(item -> toApplyResp(item))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public PageResult<RecruitApplyRespDTO> roleApplies(Long crewUserId, Long roleId, RecruitApplyQueryDTO query) {
        requireOwnedRole(crewUserId, roleId);
        long pageNo = query.getPage() == null || query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() == null || query.getSize() <= 0 ? 20 : query.getSize();
        Page<RecruitApply> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<RecruitApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecruitApply::getRecruitPostId, roleId);
        applyStatusFilter(wrapper, query.getStatus());
        wrapper.orderByDesc(RecruitApply::getCreateTime)
                .orderByDesc(RecruitApply::getRecruitApplyId);

        Page<RecruitApply> result = page(page, wrapper);
        List<RecruitApplyRespDTO> list = result.getRecords().stream()
                .map(this::toApplyResp)
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long crewUserId, Long applyId) {
        RecruitApply apply = requireApply(applyId);
        requireOwnedRole(crewUserId, apply.getRecruitPostId());
        RecruitApply update = new RecruitApply();
        update.setRecruitApplyId(applyId);
        update.setApplyStatus(DB_STATUS_APPROVED);
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long crewUserId, Long applyId, String remark) {
        RecruitApply apply = requireApply(applyId);
        requireOwnedRole(crewUserId, apply.getRecruitPostId());
        RecruitApply update = new RecruitApply();
        update.setRecruitApplyId(applyId);
        update.setApplyStatus(DB_STATUS_REJECTED);
        update.setApplyMessage(trimToNull(remark) != null ? trimToNull(remark) : apply.getApplyMessage());
        updateById(update);
    }

    @Override
    public RecruitApplyRespDTO detail(Long currentUserId, Long applyId) {
        User user = requireUser(currentUserId);
        RecruitApply apply = requireApply(applyId);
        RecruitPost post = requireRole(apply.getRecruitPostId());
        if (user.getUserType() != null && user.getUserType() == USER_TYPE_ACTOR && !currentUserId.equals(apply.getActorUserId())) {
            throw new BizException("只能查看自己的投递记录");
        }
        if (user.getUserType() != null && user.getUserType() == USER_TYPE_CREW && !currentUserId.equals(post.getUserId())) {
            throw new BizException("只能查看自己角色下的投递记录");
        }
        return toApplyResp(apply);
    }

    private RecruitApplyRespDTO toApplyResp(RecruitApply apply) {
        RecruitApplyRespDTO dto = new RecruitApplyRespDTO();
        dto.setId(apply.getRecruitApplyId());
        dto.setRoleId(apply.getRecruitPostId());
        dto.setProfileUserId(apply.getActorUserId());
        dto.setStatus(toFrontendStatus(apply.getApplyStatus()));
        dto.setRemark(defaultText(apply.getApplyMessage()));
        dto.setApplyTime(formatDateTime(apply.getCreateTime()));

        User actorUser = requireUser(apply.getActorUserId());
        ActorProfile actorProfile = actorProfileService.lambdaQuery()
                .eq(ActorProfile::getUserId, apply.getActorUserId())
                .last("limit 1")
                .one();
        dto.setActorName(firstNonBlank(actorProfile == null ? null : actorProfile.getNickName(), actorUser.getUserName(), null));
        dto.setActorAvatar(firstNonBlank(actorProfile == null ? null : actorProfile.getAvatarUrl(), actorUser.getAvatarUrl(), ""));
        dto.setActorPhone(firstNonBlank(actorProfile == null ? null : actorProfile.getPhone(), actorUser.getPhone(), ""));

        dto.setRole(recruitPostService.detail(apply.getRecruitPostId()));
        dto.setRoleName(dto.getRole().getRoleName());
        dto.setProjectName(dto.getRole().getProject() == null ? "" : dto.getRole().getProject().getTitle());
        return dto;
    }

    private void applyStatusFilter(LambdaQueryWrapper<RecruitApply> wrapper, Integer frontendStatus) {
        if (frontendStatus == null) {
            return;
        }
        if (frontendStatus == 1) {
            wrapper.in(RecruitApply::getApplyStatus, DB_STATUS_PENDING, DB_STATUS_VIEWED);
            return;
        }
        if (frontendStatus == 2) {
            wrapper.in(RecruitApply::getApplyStatus, 3, DB_STATUS_APPROVED);
            return;
        }
        if (frontendStatus == 3) {
            wrapper.eq(RecruitApply::getApplyStatus, DB_STATUS_REJECTED);
            return;
        }
        if (frontendStatus == 4) {
            wrapper.eq(RecruitApply::getApplyStatus, DB_STATUS_CANCELLED);
        }
    }

    private int toFrontendStatus(Integer applyStatus) {
        if (applyStatus != null && (applyStatus == 3 || applyStatus == DB_STATUS_APPROVED)) {
            return 2;
        }
        if (applyStatus != null && applyStatus == DB_STATUS_REJECTED) {
            return 3;
        }
        if (applyStatus != null && applyStatus == DB_STATUS_CANCELLED) {
            return 4;
        }
        return 1;
    }

    private RecruitPost requireOwnedRole(Long crewUserId, Long roleId) {
        User user = requireUser(crewUserId);
        ensureUserType(user, USER_TYPE_CREW, "只有剧组账号可以处理投递");
        RecruitPost post = requireRole(roleId);
        if (!crewUserId.equals(post.getUserId())) {
            throw new BizException("只能处理自己角色下的投递");
        }
        return post;
    }

    private RecruitPost requireOpenRole(Long roleId) {
        RecruitPost post = requireRole(roleId);
        if (post.getPostStatus() == null || post.getPostStatus() != 1) {
            throw new BizException("当前角色已停止招募");
        }
        if (post.getApplyDeadline() != null && post.getApplyDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException("当前角色已超过投递截止时间");
        }
        return post;
    }

    private RecruitPost requireRole(Long roleId) {
        RecruitPost post = recruitPostService.getById(roleId);
        if (post == null) {
            throw new BizException("角色不存在");
        }
        return post;
    }

    private RecruitApply requireApply(Long applyId) {
        RecruitApply apply = getById(applyId);
        if (apply == null) {
            throw new BizException("投递记录不存在");
        }
        return apply;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private void ensureUserType(User user, int expectedType, String message) {
        if (user.getUserType() == null || user.getUserType() != expectedType) {
            throw new BizException(message);
        }
    }

    private Long loadActorProfileId(Long actorUserId) {
        ActorProfile profile = actorProfileService.lambdaQuery()
                .eq(ActorProfile::getUserId, actorUserId)
                .last("limit 1")
                .one();
        return profile == null ? null : profile.getActorProfileId();
    }

    private int safeCount(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
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

    private String firstNonBlank(String first, String second, String defaultValue) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return defaultValue;
    }
}
