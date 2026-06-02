package com.kaipai.service.verify.impl;

import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.user.entity.User;
import com.kaipai.model.verify.dto.IdentityVerificationStatusRespDTO;
import com.kaipai.model.verify.dto.IdentityVerificationSubmitReqDTO;
import com.kaipai.model.verify.entity.IdentityVerification;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.service.referral.ReferralRecordService;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.mapper.verify.IdentityVerificationMapper;
import com.kaipai.mapper.verify.IdentityVerificationOwnerMapper;
import com.kaipai.integration.verify.IdCardCryptoSupport;
import com.kaipai.integration.verify.RealNameVerificationCommand;
import com.kaipai.integration.verify.RealNameVerificationProvider;
import com.kaipai.integration.verify.RealNameVerificationResult;
import com.kaipai.integration.verify.TencentIdCardVerificationClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityVerificationServiceImplTest {

    @Test
    void submitShouldAutoApproveWhenTencentTwoFactorMatches() {
        TestFixture fixture = newFixture(RealNameVerificationResult.matched("tencent", "req-ok", "0", "姓名和身份证号一致"));

        IdentityVerificationStatusRespDTO result = fixture.service.submit(10000L, submitRequest());

        assertEquals(2, result.getStatus());
        assertEquals("林夏", result.getRealName());
        assertEquals("110***********002X", result.getIdCardNo());
        ArgumentCaptor<IdentityVerification> recordCaptor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(fixture.identityVerificationMapper).insert(recordCaptor.capture());
        IdentityVerification record = recordCaptor.getValue();
        assertEquals(2, record.getStatus());
        assertEquals("tencent", record.getProviderCode());
        assertEquals("req-ok", record.getProviderRequestId());
        assertEquals("0", record.getProviderResultCode());
        assertEquals("110***********002X", record.getIdCardNoMasked());
        assertNotEquals("110***********002X", record.getIdCardNoCipher());
        assertNotEquals("11010519491231002X", record.getIdCardNoCipher());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(fixture.userMapper).updateById(userCaptor.capture());
        assertEquals(2, userCaptor.getValue().getRealAuthStatus());

        ArgumentCaptor<ActorProfile> profileCaptor = ArgumentCaptor.forClass(ActorProfile.class);
        verify(fixture.actorProfileMapper).updateById(profileCaptor.capture());
        assertEquals(Boolean.TRUE, profileCaptor.getValue().getIsCertified());
        verify(fixture.referralRecordService).reconcileInviteeReferral(10000L);
    }

    @Test
    void submitShouldRejectWhenTencentTwoFactorDefinitivelyMismatches() {
        TestFixture fixture = newFixture(RealNameVerificationResult.mismatch("tencent", "req-mismatch", "-1", "姓名和身份证号不一致"));

        IdentityVerificationStatusRespDTO result = fixture.service.submit(10000L, submitRequest());

        assertEquals(3, result.getStatus());
        assertEquals("实名认证信息核验未通过", result.getRejectReason());
        ArgumentCaptor<IdentityVerification> recordCaptor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(fixture.identityVerificationMapper).insert(recordCaptor.capture());
        IdentityVerification record = recordCaptor.getValue();
        assertEquals(3, record.getStatus());
        assertEquals("-1", record.getProviderResultCode());
        assertEquals("实名认证信息核验未通过", record.getRejectReason());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(fixture.userMapper).updateById(userCaptor.capture());
        assertEquals(3, userCaptor.getValue().getRealAuthStatus());
    }

    @Test
    void submitShouldKeepPendingWhenTencentProviderFails() {
        TestFixture fixture = newFixture(RealNameVerificationResult.manual("unused"));
        doThrow(new BizException("腾讯云实名核验配置未完成"))
                .when(fixture.realNameVerificationProvider)
                .verify(any(RealNameVerificationCommand.class));

        IdentityVerificationStatusRespDTO result = fixture.service.submit(10000L, submitRequest());

        assertEquals(1, result.getStatus());
        ArgumentCaptor<IdentityVerification> recordCaptor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(fixture.identityVerificationMapper).insert(recordCaptor.capture());
        IdentityVerification record = recordCaptor.getValue();
        assertEquals(1, record.getStatus());
        assertEquals("tencent", record.getProviderCode());
        assertEquals("PROVIDER_ERROR", record.getProviderResultCode());
        assertFalse(record.getProviderResultMessage().contains("11010519491231002X"));
    }

    private TestFixture newFixture(RealNameVerificationResult providerResult) {
        UserMapper userMapper = mock(UserMapper.class);
        ActorProfileMapper actorProfileMapper = mock(ActorProfileMapper.class);
        IdentityVerificationMapper identityVerificationMapper = mock(IdentityVerificationMapper.class);
        IdentityVerificationOwnerMapper ownerMapper = mock(IdentityVerificationOwnerMapper.class);
        AdminAuthContext adminAuthContext = mock(AdminAuthContext.class);
        AdminOperationLogger adminOperationLogger = mock(AdminOperationLogger.class);
        ReferralRecordService referralRecordService = mock(ReferralRecordService.class);
        RealNameVerificationProvider realNameVerificationProvider = mock(RealNameVerificationProvider.class);
        IdCardCryptoSupport idCardCryptoSupport = new IdCardCryptoSupport();
        TencentIdCardVerificationClient tencentIdCardVerificationClient = mock(TencentIdCardVerificationClient.class);

        IdentityVerificationServiceImpl service = new IdentityVerificationServiceImpl(
                userMapper,
                actorProfileMapper,
                ownerMapper,
                adminAuthContext,
                adminOperationLogger,
                referralRecordService,
                realNameVerificationProvider,
                idCardCryptoSupport,
                tencentIdCardVerificationClient);
        ReflectionTestUtils.setField(service, "baseMapper", identityVerificationMapper);

        User user = new User();
        user.setUserId(10000L);
        user.setUserName("林夏");
        when(userMapper.selectById(10000L)).thenReturn(user);

        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(20000L);
        profile.setUserId(10000L);
        profile.setNickName("林夏");
        profile.setGender(2);
        profile.setAge(24);
        profile.setHeight(168);
        profile.setLocationCity("上海");
        profile.setAvatarUrl("https://cos.example.com/avatar.jpg");
        profile.setIntro("拥有多部短剧拍摄经验，擅长都市情感和古装角色表达。");
        profile.setSkillTag("台词,武术");
        profile.setStyleTag("都市");
        profile.setVideoUrl("https://cos.example.com/video.mp4");
        profile.setPhotoUrls("[\"https://cos.example.com/photo.jpg\"]");
        profile.setExperienceDesc("拍摄经历");
        when(actorProfileMapper.selectOne(any())).thenReturn(profile);

        when(identityVerificationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(identityVerificationMapper.insert(any(IdentityVerification.class))).thenAnswer(invocation -> {
            IdentityVerification record = invocation.getArgument(0);
            record.setVerificationId(30000L);
            record.setCreateTime(LocalDateTime.of(2026, 5, 29, 12, 0));
            return 1;
        });
        when(ownerMapper.selectOne(any())).thenReturn(null);
        when(ownerMapper.insert(any())).thenReturn(1);
        when(realNameVerificationProvider.verify(any(RealNameVerificationCommand.class))).thenReturn(providerResult);

        return new TestFixture(
                service,
                userMapper,
                actorProfileMapper,
                identityVerificationMapper,
                referralRecordService,
                realNameVerificationProvider);
    }

    private IdentityVerificationSubmitReqDTO submitRequest() {
        IdentityVerificationSubmitReqDTO request = new IdentityVerificationSubmitReqDTO();
        request.setRealName("林夏");
        request.setIdCardNo("11010519491231002X");
        return request;
    }

    private record TestFixture(
            IdentityVerificationServiceImpl service,
            UserMapper userMapper,
            ActorProfileMapper actorProfileMapper,
            IdentityVerificationMapper identityVerificationMapper,
            ReferralRecordService referralRecordService,
            RealNameVerificationProvider realNameVerificationProvider
    ) {
    }
}
