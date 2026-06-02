package com.kaipai.integration.wechat.impl;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kaipai.common.exception.BizException;
import com.kaipai.service.wechat.WechatMiniProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WechatMiniProgramServiceImpl implements WechatMiniProgramService {

    private static final String WECHAT_ACCESS_TOKEN_CACHE_KEY = "wechat:miniapp:access-token";
    private static final String ACCESS_TOKEN_API = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String UNLIMITED_QR_CODE_API = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=%s";
    private static final String PHONE_NUMBER_API = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=%s";

    private final StringRedisTemplate redisTemplate;

    @Value("${wechat.miniapp.app-id:}")
    private String wechatMiniappAppId;

    @Value("${wechat.miniapp.app-secret:}")
    private String wechatMiniappAppSecret;

    @Value("${wechat.miniapp.env-version:develop}")
    private String wechatMiniappEnvVersion;

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(wechatMiniappAppId) && StringUtils.hasText(wechatMiniappAppSecret);
    }

    @Override
    public String getAccessToken() {
        requireConfigured();
        String cached = redisTemplate.opsForValue().get(WECHAT_ACCESS_TOKEN_CACHE_KEY);
        if (StringUtils.hasText(cached)) {
            return cached.trim();
        }

        String body = HttpUtil.get(String.format(
                ACCESS_TOKEN_API,
                wechatMiniappAppId.trim(),
                wechatMiniappAppSecret.trim()
        ), 8000);
        JSONObject response = JSONUtil.parseObj(body);
        String accessToken = response.getStr("access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new BizException("微信 access_token 获取失败：" + response.getStr("errmsg", "unknown_error"));
        }

        long expiresIn = response.getLong("expires_in", 7200L);
        redisTemplate.opsForValue().set(
                WECHAT_ACCESS_TOKEN_CACHE_KEY,
                accessToken.trim(),
                Math.max(60L, expiresIn - 300L),
                TimeUnit.SECONDS
        );
        return accessToken.trim();
    }

    @Override
    public String getPhoneNumber(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("微信手机号授权 code 不能为空");
        }
        String body = HttpRequest.post(String.format(PHONE_NUMBER_API, getAccessToken()))
                .contentType(ContentType.JSON.getValue())
                .body(JSONUtil.createObj().set("code", code.trim()).toString())
                .timeout(8000)
                .execute()
                .body();
        JSONObject response = JSONUtil.parseObj(body);
        Integer errCode = response.getInt("errcode");
        if (errCode != null && errCode != 0) {
            throw new BizException("微信手机号换取失败：" + response.getStr("errmsg", "unknown_error"));
        }
        JSONObject phoneInfo = response.getJSONObject("phone_info");
        String phone = phoneInfo == null ? null : phoneInfo.getStr("purePhoneNumber");
        if (!StringUtils.hasText(phone)) {
            phone = phoneInfo == null ? null : phoneInfo.getStr("phoneNumber");
        }
        if (!StringUtils.hasText(phone)) {
            throw new BizException("微信未返回可用手机号");
        }
        return phone.trim();
    }

    @Override
    public byte[] getUnlimitedQrCode(String scene, String page, Integer width, boolean checkPath) {
        requireConfigured();

        JSONObject payload = JSONUtil.createObj()
                .set("scene", scene)
                .set("page", page)
                .set("check_path", checkPath)
                .set("env_version", normalizeEnvVersion())
                .set("width", normalizeWidth(width));

        HttpResponse response = HttpRequest.post(String.format(UNLIMITED_QR_CODE_API, getAccessToken()))
                .contentType(ContentType.JSON.getValue())
                .body(payload.toString())
                .timeout(10000)
                .execute();

        byte[] bodyBytes = response.bodyBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            throw new BizException("微信小程序码返回空内容");
        }

        if (looksLikeJson(response.header("Content-Type"), bodyBytes)) {
            JSONObject json = JSONUtil.parseObj(new String(bodyBytes, StandardCharsets.UTF_8));
            Integer errCode = json.getInt("errcode");
            if (errCode != null && errCode != 0) {
                throw new BizException("微信小程序码生成失败：" + json.getStr("errmsg", "unknown_error"));
            }
        }

        return bodyBytes;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BizException("微信小程序 appId/appSecret 未配置");
        }
    }

    private String normalizeEnvVersion() {
        String envVersion = StringUtils.hasText(wechatMiniappEnvVersion) ? wechatMiniappEnvVersion.trim() : "develop";
        return switch (envVersion) {
            case "release", "trial", "develop" -> envVersion;
            default -> "develop";
        };
    }

    private int normalizeWidth(Integer width) {
        if (width == null) {
            return 320;
        }
        return Math.max(280, Math.min(1280, width));
    }

    private boolean looksLikeJson(String contentType, byte[] bodyBytes) {
        if (StringUtils.hasText(contentType) && contentType.toLowerCase().contains("json")) {
            return true;
        }
        for (byte bodyByte : bodyBytes) {
            char value = (char) bodyByte;
            if (Character.isWhitespace(value)) {
                continue;
            }
            return value == '{';
        }
        return false;
    }
}
