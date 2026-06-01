package com.kaipai.service.referral.impl;

import com.kaipai.common.util.QrCodeBase64Util;
import com.kaipai.model.referral.dto.InviteQrCodeResult;
import com.kaipai.service.referral.InviteQrCodeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InviteQrCodeServiceImpl implements InviteQrCodeService {

    private static final String INVITE_PAGE = "pages/login/index";
    private static final int INVITE_QR_SIZE = 320;

    @Override
    public InviteQrCodeResult buildInviteQrCode(String inviteCode) {
        String normalizedInviteCode = normalizeInviteCode(inviteCode);
        String scene = "inviteCode=" + normalizedInviteCode;
        String invitePath = INVITE_PAGE + "?" + scene;

        try {
            return InviteQrCodeResult.builder()
                    .dataUrl(QrCodeBase64Util.generatePngDataUrl(invitePath, INVITE_QR_SIZE, INVITE_QR_SIZE, 1))
                    .qrCodeType("invitePathQr")
                    .scene(scene)
                    .page(INVITE_PAGE)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("邀请码二维码生成失败：" + ex.getMessage(), ex);
        }
    }

    private String normalizeInviteCode(String inviteCode) {
        if (!StringUtils.hasText(inviteCode)) {
            throw new IllegalArgumentException("邀请码不能为空");
        }
        String normalizedInviteCode = inviteCode.trim().toUpperCase();
        if (!normalizedInviteCode.matches("[A-Z0-9]{1,32}")) {
            throw new IllegalArgumentException("邀请码格式错误");
        }
        return normalizedInviteCode;
    }
}
