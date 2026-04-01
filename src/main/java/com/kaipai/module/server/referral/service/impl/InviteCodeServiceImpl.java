package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.referral.mapper.InviteCodeMapper;
import com.kaipai.module.server.referral.service.InviteCodeService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class InviteCodeServiceImpl extends ServiceImpl<InviteCodeMapper, InviteCode> implements InviteCodeService {

    private static final int STATUS_ACTIVE = 1;
    private static final int USER_TYPE_ACTOR = 1;
    private static final int REAL_AUTH_APPROVED = 2;
    private static final String INVITE_CODE_CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final UserMapper userMapper;

    @Override
    public InviteCode ensureActiveInviteCode(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (user.getUserType() == null || user.getUserType() != USER_TYPE_ACTOR) {
            throw new BizException("只有演员账号可以生成邀请码");
        }
        if (user.getRealAuthStatus() == null || user.getRealAuthStatus() != REAL_AUTH_APPROVED) {
            throw new BizException("完成实名认证后才能生成邀请码");
        }

        InviteCode existing = getOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getUserId, userId)
                .eq(InviteCode::getStatus, STATUS_ACTIVE)
                .orderByDesc(InviteCode::getCreateTime)
                .orderByDesc(InviteCode::getInviteCodeId)
                .last("limit 1"), false);
        if (existing != null) {
            return existing;
        }

        InviteCode inviteCode = new InviteCode();
        inviteCode.setUserId(userId);
        inviteCode.setStatus(STATUS_ACTIVE);
        inviteCode.setCode(generateUniqueCode());
        save(inviteCode);
        return inviteCode;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode(8);
            if (!exists(new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getCode, code))) {
                return code;
            }
        }
        return randomCode(10);
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            builder.append(INVITE_CODE_CHARSET.charAt(random.nextInt(INVITE_CODE_CHARSET.length())));
        }
        return builder.toString();
    }
}
