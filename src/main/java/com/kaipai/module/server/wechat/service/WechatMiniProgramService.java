package com.kaipai.module.server.wechat.service;

public interface WechatMiniProgramService {

    boolean isConfigured();

    String getAccessToken();

    String getPhoneNumber(String code);

    byte[] getUnlimitedQrCode(String scene, String page, Integer width, boolean checkPath);
}
