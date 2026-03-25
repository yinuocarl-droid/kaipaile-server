package com.kaipai.module.server.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.common.util.JwtUtil;
import com.kaipai.module.model.auth.dto.LoginReqDTO;
import com.kaipai.module.model.auth.dto.LoginRespDTO;
import com.kaipai.module.model.auth.dto.RegisterReqDTO;
import com.kaipai.module.server.auth.service.AuthService;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final long SMS_CODE_EXPIRE_MINUTES = 5;

    @Override
    public String sendCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone, code, SMS_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        // 开发阶段直接返回验证码，上线前替换为真实短信 SDK
        log.info("【开发模式】手机号 {} 验证码: {}", phone, code);
        return code;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO register(RegisterReqDTO dto) {
        verifyCode(dto.getPhone(), dto.getCode());

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));
        if (count > 0) {
            throw new BizException(ResultCode.PHONE_ALREADY_BOUND);
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setAccount(dto.getPhone());
        user.setPassword("");
        user.setUserName(dto.getNickName() != null ? dto.getNickName()
                : "用户" + dto.getPhone().substring(7));
        user.setUserType(dto.getUserType());
        user.setRegisterSource(1);
        user.setRealAuthStatus(0);
        user.setStatus(1);
        user.setCreateUserName("");
        user.setUpdateUserName("");
        userMapper.insert(user);

        redisTemplate.delete(SMS_CODE_PREFIX + dto.getPhone());

        String token = jwtUtil.generateToken(user.getUserId(), user.getPhone(), user.getUserType());
        return LoginRespDTO.builder()
                .token(token)
                .userId(user.getUserId())
                .userType(user.getUserType())
                .nickName(user.getUserName())
                .build();
    }

    @Override
    public LoginRespDTO login(LoginReqDTO dto) {
        verifyCode(dto.getPhone(), dto.getCode());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw new BizException("该手机号未注册，请先注册");
        }
        if (user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        User update = new User();
        update.setUserId(user.getUserId());
        update.setLastLoginTime(LocalDateTime.now());
        update.setUpdateUserName("");
        userMapper.updateById(update);

        redisTemplate.delete(SMS_CODE_PREFIX + dto.getPhone());

        String token = jwtUtil.generateToken(user.getUserId(), user.getPhone(), user.getUserType());
        return LoginRespDTO.builder()
                .token(token)
                .userId(user.getUserId())
                .userType(user.getUserType())
                .nickName(user.getUserName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private void verifyCode(String phone, String inputCode) {
        String cachedCode = redisTemplate.opsForValue().get(SMS_CODE_PREFIX + phone);
        if (cachedCode == null) {
            throw new BizException(ResultCode.VERIFY_CODE_ERROR);
        }
        if (!cachedCode.equals(inputCode)) {
            throw new BizException(ResultCode.VERIFY_CODE_ERROR);
        }
    }
}
