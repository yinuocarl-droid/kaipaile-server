package com.kaipai.module.server.auth.service;

import com.kaipai.module.model.auth.dto.LoginReqDTO;
import com.kaipai.module.model.auth.dto.LoginRespDTO;
import com.kaipai.module.model.auth.dto.RegisterReqDTO;
import com.kaipai.module.model.auth.dto.WechatLoginReqDTO;

public interface AuthService {

    /**
     * 发送验证码。
     * dev 通道可返回验证码；生产短信通道不返回验证码。
     */
    String sendCode(String phone);

    /**
     * 手机号+验证码注册
     */
    LoginRespDTO register(RegisterReqDTO dto);

    /**
     * 手机号+验证码登录
     */
    LoginRespDTO login(LoginReqDTO dto);

    /**
     * 微信手机号一键登录
     */
    LoginRespDTO loginByWechat(WechatLoginReqDTO dto);
}
