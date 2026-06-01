package com.kaipai.module.server.auth.service.impl;

import com.kaipai.common.util.JwtUtil;
import com.kaipai.module.model.auth.dto.LoginRespDTO;
import com.kaipai.module.model.auth.dto.WechatLoginReqDTO;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.auth.sms.SmsCodeSendCommand;
import com.kaipai.module.server.auth.sms.SmsCodeSendResult;
import com.kaipai.module.server.auth.sms.SmsCodeSender;
import com.kaipai.module.server.auth.sms.SmsProperties;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import com.kaipai.module.server.referral.service.ReferralRegistrationService;
import com.kaipai.module.server.user.mapper.UserMapper;
import com.kaipai.module.server.wechat.service.WechatMiniProgramService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    @Test
    void wechatLoginShouldRegisterNewUserAsActor() {
        UserMapper userMapper = mock(UserMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        ReferralRegistrationService referralRegistrationService = mock(ReferralRegistrationService.class);
        ReferralRecordService referralRecordService = mock(ReferralRecordService.class);
        WechatMiniProgramService wechatMiniProgramService = mock(WechatMiniProgramService.class);
        SmsCodeSender smsCodeSender = mock(SmsCodeSender.class);
        SmsProperties smsProperties = mock(SmsProperties.class);
        AuthServiceImpl service = new AuthServiceImpl(
                userMapper,
                redisTemplate,
                jwtUtil,
                referralRegistrationService,
                referralRecordService,
                wechatMiniProgramService,
                smsCodeSender,
                smsProperties);
        InviteCode inviteCode = new InviteCode();

        when(wechatMiniProgramService.isConfigured()).thenReturn(true);
        when(wechatMiniProgramService.getPhoneNumber("wx-phone-code")).thenReturn("13800138000");
        when(referralRegistrationService.prepareInviteOnRegister(any(User.class), any())).thenReturn(inviteCode);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setUserId(10001L);
            return 1;
        });
        when(referralRecordService.countValidInviteCount(10001L)).thenReturn(0);
        when(jwtUtil.generateToken(10001L, "13800138000", 1)).thenReturn("token-10001");

        WechatLoginReqDTO dto = new WechatLoginReqDTO();
        dto.setCode("wx-phone-code");
        dto.setInviteCode("KM7P4A");
        dto.setDeviceFingerprint("device-001");

        LoginRespDTO result = service.loginByWechat(dto);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();

        assertEquals("13800138000", inserted.getPhone());
        assertEquals("13800138000", inserted.getAccount());
        assertEquals(1, inserted.getUserType());
        assertEquals(3, inserted.getRegisterSource());
        assertEquals(0, inserted.getRealAuthStatus());
        assertEquals(1, inserted.getStatus());
        assertNotNull(inserted.getLastLoginTime());
        verify(referralRegistrationService).persistInviteOnRegister(inserted, inviteCode);

        assertEquals("token-10001", result.getToken());
        assertEquals(10001L, result.getUserId());
        assertEquals("13800138000", result.getPhone());
        assertEquals(1, result.getUserType());
    }

    @Test
    void sendCodeShouldExposeCodeOnlyWhenProviderAllowsIt() {
        UserMapper userMapper = mock(UserMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        ReferralRegistrationService referralRegistrationService = mock(ReferralRegistrationService.class);
        ReferralRecordService referralRecordService = mock(ReferralRecordService.class);
        WechatMiniProgramService wechatMiniProgramService = mock(WechatMiniProgramService.class);
        SmsCodeSender smsCodeSender = mock(SmsCodeSender.class);
        SmsProperties smsProperties = mock(SmsProperties.class);
        AuthServiceImpl service = new AuthServiceImpl(
                userMapper,
                redisTemplate,
                jwtUtil,
                referralRegistrationService,
                referralRecordService,
                wechatMiniProgramService,
                smsCodeSender,
                smsProperties);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(smsProperties.getCodeExpireMinutes()).thenReturn(5L);
        when(smsCodeSender.sendCode(any(SmsCodeSendCommand.class))).thenReturn(SmsCodeSendResult.dev());

        String exposedCode = service.sendCode("13800138000");

        assertNotNull(exposedCode);
        assertEquals(6, exposedCode.length());
        verify(valueOperations).set(eq("sms:code:13800138000"), eq(exposedCode), eq(5L), eq(TimeUnit.MINUTES));
    }
}
