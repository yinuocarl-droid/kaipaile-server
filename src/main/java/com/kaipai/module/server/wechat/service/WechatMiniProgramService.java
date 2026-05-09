package com.kaipai.module.server.wechat.service;

public interface WechatMiniProgramService {

    boolean isConfigured();

    String getAccessToken();

    byte[] getUnlimitedQrCode(String scene, String page, Integer width, boolean checkPath);
}
