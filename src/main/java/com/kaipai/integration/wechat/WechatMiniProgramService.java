package com.kaipai.service.wechat;

public interface WechatMiniProgramService {

    boolean isConfigured();

    String getAccessToken();

    String getPhoneNumber(String code);

    byte[] getUnlimitedQrCode(String scene, String page, Integer width, boolean checkPath);
}
