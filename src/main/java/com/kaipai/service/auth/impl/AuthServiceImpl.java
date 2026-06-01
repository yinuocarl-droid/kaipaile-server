package com.kaipai.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.common.util.JwtUtil;
import com.kaipai.model.auth.dto.LoginReqDTO;
import com.kaipai.model.auth.dto.LoginRespDTO;
import com.kaipai.model.auth.dto.RegisterReqDTO;
import com.kaipai.model.auth.dto.WechatLoginReqDTO;
import com.kaipai.model.referral.entity.InviteCode;
import com.kaipai.service.auth.AuthService;
import com.kaipai.integration.sms.SmsCodeSendCommand;
import com.kaipai.integration.sms.SmsCodeSendResult;
import com.kaipai.integration.sms.SmsCodeSender;
import com.kaipai.integration.sms.SmsProperties;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.referral.ReferralRecordService;
import com.kaipai.service.referral.ReferralRegistrationService;
import com.kaipai.service.wechat.WechatMiniProgramService;
import com.kaipai.mapper.user.UserMapper;
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
    private final ReferralRegistrationService referralRegistrationService;
    private final ReferralRecordService referralRecordService;
    private final WechatMiniProgramService wechatMiniProgramService;
    private final SmsCodeSender smsCodeSender;
    private final SmsProperties smsProperties;

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final int USER_TYPE_ACTOR = 1;
    private static final int REGISTER_SOURCE_WECHAT_MINIAPP = 3;

    @Override
    public String sendCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        long expireMinutes = Math.max(1, smsProperties.getCodeExpireMinutes());
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone, code, expireMinutes, TimeUnit.MINUTES);
        try {
            SmsCodeSendResult result = smsCodeSender.sendCode(new SmsCodeSendCommand(phone, code, expireMinutes, "login"));
            log.info("验证码发送完成 provider={}, requestId={}, serialNo={}, phone={}",
                    result.providerCode(),
                    result.requestId(),
                    result.serialNo(),
                    maskPhone(phone));
            return result.exposeCodeToClient() ? code : null;
        } catch (RuntimeException error) {
            redisTemplate.delete(SMS_CODE_PREFIX + phone);
            throw error;
        }
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
        user.setValidInviteCount(0);
        user.setStatus(1);
        user.setCreateUserName("");
        user.setUpdateUserName("");
        InviteCode inviteCode = referralRegistrationService.prepareInviteOnRegister(user, dto);
        userMapper.insert(user);
        referralRegistrationService.persistInviteOnRegister(user, inviteCode);

        redisTemplate.delete(SMS_CODE_PREFIX + dto.getPhone());
        return buildLoginResp(user);
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
        user.setLastLoginTime(update.getLastLoginTime());

        redisTemplate.delete(SMS_CODE_PREFIX + dto.getPhone());
        return buildLoginResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO loginByWechat(WechatLoginReqDTO dto) {
        String phone = resolvePhoneByWechatCode(dto.getCode());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
                .last("limit 1"));
        if (user == null) {
            user = registerWechatUser(phone, dto);
        } else {
            touchLogin(user);
        }
        return buildLoginResp(user);
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

    private User registerWechatUser(String phone, WechatLoginReqDTO dto) {
        User user = new User();
        user.setPhone(phone);
        user.setAccount(phone);
        user.setPassword("");
        user.setUserName("开拍用户" + phone.substring(Math.max(0, phone.length() - 4)));
        user.setUserType(USER_TYPE_ACTOR);
        user.setRegisterSource(REGISTER_SOURCE_WECHAT_MINIAPP);
        user.setRealAuthStatus(0);
        user.setValidInviteCount(0);
        user.setStatus(1);
        user.setCreateUserName("");
        user.setUpdateUserName("");
        user.setLastLoginTime(LocalDateTime.now());

        RegisterReqDTO registerReq = new RegisterReqDTO();
        registerReq.setInviteCode(dto.getInviteCode());
        registerReq.setDeviceFingerprint(dto.getDeviceFingerprint());
        InviteCode inviteCode = referralRegistrationService.prepareInviteOnRegister(user, registerReq);
        userMapper.insert(user);
        referralRegistrationService.persistInviteOnRegister(user, inviteCode);
        return user;
    }

    private void touchLogin(User user) {
        User update = new User();
        update.setUserId(user.getUserId());
        update.setLastLoginTime(LocalDateTime.now());
        update.setUpdateUserName("");
        userMapper.updateById(update);
        user.setLastLoginTime(update.getLastLoginTime());
    }

    private String resolvePhoneByWechatCode(String code) {
        if (!wechatMiniProgramService.isConfigured()) {
            throw new BizException("微信登录未配置小程序 appId/appSecret");
        }
        return wechatMiniProgramService.getPhoneNumber(code);
    }

    private LoginRespDTO buildLoginResp(User user) {
        referralRecordService.reconcileInviteeReferral(user.getUserId());
        int validInviteCount = referralRecordService.countValidInviteCount(user.getUserId());
        user.setValidInviteCount(validInviteCount);
        String token = jwtUtil.generateToken(user.getUserId(), user.getPhone(), user.getUserType());
        return LoginRespDTO.builder()
                .token(token)
                .userId(user.getUserId())
                .phone(user.getPhone())
                .userType(user.getUserType())
                .nickName(user.getUserName())
                .avatarUrl(user.getAvatarUrl())
                .registeredAt(user.getCreateTime())
                .realAuthStatus(user.getRealAuthStatus())
                .invitedByUserId(user.getInvitedByUserId())
                .validInviteCount(validInviteCount)
                .build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
