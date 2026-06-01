package com.kaipai.module.server.verify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.verify.dto.IdentityVerificationAuditReqDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationDetailRespDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationListItemDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationListReqDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationStatusRespDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationSubmitReqDTO;
import com.kaipai.module.model.verify.entity.IdentityVerification;
import com.kaipai.module.model.verify.entity.IdentityVerificationOwner;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.actor.support.ActorProfileCompletionCalculator;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import com.kaipai.module.server.user.mapper.UserMapper;
import com.kaipai.module.server.verify.mapper.IdentityVerificationMapper;
import com.kaipai.module.server.verify.mapper.IdentityVerificationOwnerMapper;
import com.kaipai.module.server.verify.realname.IdCardCryptoSupport;
import com.kaipai.module.server.verify.realname.RealNameVerificationCommand;
import com.kaipai.module.server.verify.realname.RealNameVerificationProvider;
import com.kaipai.module.server.verify.realname.RealNameVerificationResult;
import com.kaipai.module.server.verify.service.IdentityVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IdentityVerificationServiceImpl extends ServiceImpl<IdentityVerificationMapper, IdentityVerification> implements IdentityVerificationService {

    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final IdentityVerificationOwnerMapper identityVerificationOwnerMapper;
    private final AdminAuthContext adminAuthContext;
    private final AdminOperationLogger adminOperationLogger;
    private final ReferralRecordService referralRecordService;
    private final RealNameVerificationProvider realNameVerificationProvider;
    private final IdCardCryptoSupport idCardCryptoSupport;

    private static final int STATUS_PENDING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;

    @Override
    public IdentityVerificationStatusRespDTO currentStatus(Long userId) {
        User user = userMapper.selectById(userId);
        IdentityVerification latestRecord = selectLatestByUserId(userId);
        if (latestRecord == null) {
            IdentityVerificationStatusRespDTO dto = new IdentityVerificationStatusRespDTO();
            dto.setStatus(user == null || user.getRealAuthStatus() == null ? 0 : user.getRealAuthStatus());
            return dto;
        }
        return toStatusResp(latestRecord);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdentityVerificationStatusRespDTO submit(Long userId, IdentityVerificationSubmitReqDTO req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        String realName = req.getRealName() == null ? "" : req.getRealName().trim();
        if (realName.isEmpty()) {
            throw new BizException("真实姓名不能为空");
        }
        String normalizedIdCardNo = normalizeIdCardNo(req.getIdCardNo());
        String idCardHash = idCardCryptoSupport.hash(normalizedIdCardNo);

        IdentityVerification latestRecord = selectLatestByUserId(userId);
        if (latestRecord != null) {
            Integer latestStatus = latestRecord.getStatus();
            if (latestStatus != null && latestStatus == STATUS_PENDING) {
                throw new BizException("当前已有待审核认证申请");
            }
            if (latestStatus != null && latestStatus == STATUS_APPROVED) {
                throw new BizException("已完成实名认证，无需重复提交");
            }
        }

        ensureIdCardOwner(userId, idCardHash);

        IdentityVerification duplicateRecord = getOne(new QueryWrapper<IdentityVerification>().lambda()
                .eq(IdentityVerification::getIdCardHash, idCardHash)
                .ne(IdentityVerification::getUserId, userId)
                .orderByDesc(IdentityVerification::getCreateTime)
                .orderByDesc(IdentityVerification::getVerificationId)
                .last("LIMIT 1"), false);
        if (duplicateRecord != null) {
            throw new BizException("该身份证号已被其他账号提交");
        }

        ActorProfile profile = actorProfileMapper.selectOne(new QueryWrapper<ActorProfile>().lambda()
                .eq(ActorProfile::getUserId, userId)
                .last("LIMIT 1"));
        int profileCompletion = ActorProfileCompletionCalculator.calculate(profile);
        if (profileCompletion < 70) {
            throw new BizException("请先将档案完成度提升到 70%");
        }

        IdentityVerification record = new IdentityVerification();
        record.setUserId(userId);
        record.setRealName(realName);
        record.setIdCardNoCipher(idCardCryptoSupport.encrypt(normalizedIdCardNo));
        record.setIdCardNoMasked(idCardCryptoSupport.mask(normalizedIdCardNo));
        record.setIdCardHash(idCardHash);
        record.setRejectReason(null);
        record.setReviewerId(null);
        record.setReviewedAt(null);
        record.setSnapshotProfileCompletion(profileCompletion);
        RealNameVerificationResult verificationResult = verifyRealName(userId, realName, normalizedIdCardNo);
        applyProviderResult(record, verificationResult);
        save(record);

        int status = record.getStatus() == null ? STATUS_PENDING : record.getStatus();
        User updateUser = new User();
        updateUser.setUserId(userId);
        updateUser.setRealAuthStatus(status);
        updateUser.setUpdateUserId(userId);
        updateUser.setUpdateUserName(resolveUserUpdateName(user));
        userMapper.updateById(updateUser);

        if (profile != null) {
            profile.setRealName(realName);
            profile.setIsCertified(status == STATUS_APPROVED);
            actorProfileMapper.updateById(profile);
        }

        if (status == STATUS_APPROVED) {
            referralRecordService.reconcileInviteeReferral(userId);
        }

        return toStatusResp(record);
    }

    @Override
    public PageResult<IdentityVerificationListItemDTO> adminList(IdentityVerificationListReqDTO req) {
        QueryWrapper<IdentityVerification> wrapper = new QueryWrapper<>();
        if (req.getStatus() != null) {
            wrapper.eq("status", req.getStatus());
        }
        if (req.getUserId() != null) {
            wrapper.eq("user_id", req.getUserId());
        }
        if (req.getSubmitTimeFrom() != null) {
            wrapper.ge("create_time", req.getSubmitTimeFrom());
        }
        if (req.getSubmitTimeTo() != null) {
            wrapper.le("create_time", req.getSubmitTimeTo());
        }
        wrapper.orderByDesc("create_time");
        long total = baseMapper.selectCount(wrapper);
        if (total == 0) {
            return PageResult.empty();
        }
        int pageNo = Math.max(req.getPageNo(), 1);
        int pageSize = Math.max(req.getPageSize(), 10);
        int offset = (pageNo - 1) * pageSize;
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<IdentityVerification> records = baseMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return PageResult.empty();
        }
        Set<Long> userIds = records.stream().map(IdentityVerification::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getUserId, u -> u));
        List<IdentityVerificationListItemDTO> list = records.stream().map(record -> {
            IdentityVerificationListItemDTO item = new IdentityVerificationListItemDTO();
            item.setVerificationId(record.getVerificationId());
            item.setUserId(record.getUserId());
            item.setRealName(record.getRealName());
            item.setStatus(record.getStatus());
            item.setSubmitTime(record.getCreateTime());
            User user = userMap.get(record.getUserId());
            if (user != null) {
                item.setPhone(user.getPhone());
                item.setUserName(user.getUserName());
            }
            return item;
        }).collect(Collectors.toList());
        return new PageResult<>(total, list);
    }

    @Override
    public IdentityVerificationDetailRespDTO adminDetail(Long id) {
        IdentityVerification record = getById(id);
        if (record == null) {
            throw new BizException("实名认证记录不存在");
        }
        IdentityVerificationDetailRespDTO dto = new IdentityVerificationDetailRespDTO();
        dto.setVerificationId(record.getVerificationId());
        dto.setUserId(record.getUserId());
        dto.setRealName(record.getRealName());
        dto.setIdCardNoCipher(record.getIdCardNoMasked() == null ? record.getIdCardNoCipher() : record.getIdCardNoMasked());
        dto.setIdCardNoMasked(record.getIdCardNoMasked() == null ? record.getIdCardNoCipher() : record.getIdCardNoMasked());
        dto.setStatus(record.getStatus());
        dto.setRejectReason(record.getRejectReason());
        dto.setSubmitTime(record.getCreateTime());
        dto.setReviewedAt(record.getReviewedAt());
        dto.setProviderCode(record.getProviderCode());
        dto.setProviderRequestId(record.getProviderRequestId());
        dto.setProviderResultCode(record.getProviderResultCode());
        dto.setProviderResultMessage(record.getProviderResultMessage());
        dto.setProviderVerifiedAt(record.getProviderVerifiedAt());
        User user = userMapper.selectById(record.getUserId());
        if (user != null) {
            dto.setPhone(user.getPhone());
            dto.setUserName(user.getUserName());
        }
        ActorProfile profile = actorProfileMapper.selectOne(new QueryWrapper<ActorProfile>().lambda()
                .eq(ActorProfile::getUserId, record.getUserId())
        );
        if (profile != null) {
            dto.setActorCertified(Boolean.TRUE.equals(profile.getIsCertified()));
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, IdentityVerificationAuditReqDTO req) {
        review(id, req, STATUS_APPROVED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, IdentityVerificationAuditReqDTO req) {
        review(id, req, STATUS_REJECTED);
    }

    private void review(Long id, IdentityVerificationAuditReqDTO req, int newStatus) {
        IdentityVerification record = getById(id);
        if (record == null) {
            throw new BizException("实名认证记录不存在");
        }
        Integer currentStatus = record.getStatus();
        if (currentStatus == null || currentStatus != STATUS_PENDING) {
            throw new BizException("只有待审核记录可以操作");
        }
        Map<String, Object> beforeSnapshot = snapshot(record);
        record.setStatus(newStatus);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewerId(adminAuthContext.getCurrentAdminUserId());
        if (newStatus == STATUS_REJECTED) {
            record.setRejectReason(req.getRemark());
        } else {
            record.setRejectReason(null);
        }
        updateById(record);
        User user = userMapper.selectById(record.getUserId());
        if (user != null) {
            user.setRealAuthStatus(newStatus == STATUS_APPROVED ? 2 : 3);
            userMapper.updateById(user);
        }
        ActorProfile profile = actorProfileMapper.selectOne(new QueryWrapper<ActorProfile>().lambda()
                .eq(ActorProfile::getUserId, record.getUserId())
        );
        if (profile != null) {
            profile.setIsCertified(newStatus == STATUS_APPROVED);
            if (newStatus == STATUS_APPROVED) {
                profile.setRealName(record.getRealName());
            }
            actorProfileMapper.updateById(profile);
        }
        if (newStatus == STATUS_APPROVED) {
            referralRecordService.reconcileInviteeReferral(record.getUserId());
        }
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("verify")
                .operationCode(newStatus == STATUS_APPROVED ? "approve" : "reject")
                .targetType("identity_verification")
                .targetId(record.getVerificationId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(record))
                .extraContext(buildExtraContext(record, req))
                .operationResult(1)
                .build());
    }

    private Map<String, Object> snapshot(IdentityVerification record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("verificationId", record.getVerificationId());
        snapshot.put("userId", record.getUserId());
        snapshot.put("status", record.getStatus());
        snapshot.put("reviewerId", record.getReviewerId());
        snapshot.put("rejectReason", record.getRejectReason());
        snapshot.put("reviewedAt", record.getReviewedAt());
        return snapshot;
    }

    private Map<String, Object> buildExtraContext(IdentityVerification record, IdentityVerificationAuditReqDTO req) {
        Map<String, Object> extraContext = new LinkedHashMap<>();
        extraContext.put("verification_id", record.getVerificationId());
        extraContext.put("apply_user_id", record.getUserId());
        extraContext.put("verify_status_after", record.getStatus());
        extraContext.put("audit_remark", req.getRemark());
        if (record.getStatus() != null && record.getStatus() == STATUS_REJECTED) {
            extraContext.put("reason", req.getRemark());
        }
        return extraContext;
    }

    private IdentityVerification selectLatestByUserId(Long userId) {
        return getOne(new QueryWrapper<IdentityVerification>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .orderByDesc("verification_id")
                .last("LIMIT 1"), false);
    }

    private void ensureIdCardOwner(Long userId, String idCardHash) {
        IdentityVerificationOwner existingOwner = selectOwnerByHash(idCardHash);
        if (existingOwner != null) {
            if (!userId.equals(existingOwner.getUserId())) {
                throw new BizException("该身份证号已被其他账号提交");
            }
            return;
        }

        IdentityVerificationOwner owner = new IdentityVerificationOwner();
        owner.setUserId(userId);
        owner.setIdCardHash(idCardHash);
        try {
            identityVerificationOwnerMapper.insert(owner);
        } catch (DuplicateKeyException ex) {
            IdentityVerificationOwner concurrentOwner = selectOwnerByHash(idCardHash);
            if (concurrentOwner != null && !userId.equals(concurrentOwner.getUserId())) {
                throw new BizException("该身份证号已被其他账号提交");
            }
            if (concurrentOwner == null) {
                throw ex;
            }
        }
    }

    private IdentityVerificationOwner selectOwnerByHash(String idCardHash) {
        return identityVerificationOwnerMapper.selectOne(new QueryWrapper<IdentityVerificationOwner>().lambda()
                .eq(IdentityVerificationOwner::getIdCardHash, idCardHash)
                .last("LIMIT 1"));
    }

    private IdentityVerificationStatusRespDTO toStatusResp(IdentityVerification record) {
        IdentityVerificationStatusRespDTO dto = new IdentityVerificationStatusRespDTO();
        dto.setStatus(record.getStatus());
        dto.setRealName(record.getRealName());
        dto.setIdCardNo(record.getIdCardNoMasked() == null ? record.getIdCardNoCipher() : record.getIdCardNoMasked());
        dto.setRejectReason(record.getRejectReason());
        dto.setSubmittedAt(record.getCreateTime());
        dto.setReviewedAt(record.getReviewedAt());
        return dto;
    }

    private String normalizeIdCardNo(String idCardNo) {
        String normalized = idCardNo == null ? "" : idCardNo.replace(" ", "").trim().toUpperCase();
        if (!normalized.matches("\\d{17}[\\dX]")) {
            throw new BizException("身份证号格式不正确");
        }
        return normalized;
    }

    private String resolveUserUpdateName(User user) {
        if (user == null || user.getUserName() == null) {
            return "";
        }
        return user.getUserName().trim();
    }

    private RealNameVerificationResult verifyRealName(Long userId, String realName, String normalizedIdCardNo) {
        try {
            return realNameVerificationProvider.verify(new RealNameVerificationCommand(userId, realName, normalizedIdCardNo));
        } catch (BizException error) {
            return RealNameVerificationResult.manualReview("tencent", null, "PROVIDER_ERROR", compactProviderMessage(error.getMessage()));
        }
    }

    private void applyProviderResult(IdentityVerification record, RealNameVerificationResult result) {
        record.setProviderCode(result.providerCode());
        record.setProviderRequestId(result.requestId());
        record.setProviderResultCode(result.resultCode());
        record.setProviderResultMessage(compactProviderMessage(result.resultMessage()));
        record.setProviderVerifiedAt(LocalDateTime.now());
        if (result.matched()) {
            record.setStatus(STATUS_APPROVED);
            record.setRejectReason(null);
            record.setReviewedAt(LocalDateTime.now());
            return;
        }
        if (result.definitive()) {
            record.setStatus(STATUS_REJECTED);
            record.setRejectReason("实名认证信息核验未通过");
            record.setReviewedAt(LocalDateTime.now());
            return;
        }
        record.setStatus(STATUS_PENDING);
        record.setRejectReason(null);
    }

    private String compactProviderMessage(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("\\d{17}[\\dXx]", "[ID_CARD]");
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }
}
