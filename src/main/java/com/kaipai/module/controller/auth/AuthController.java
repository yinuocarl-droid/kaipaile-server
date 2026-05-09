package com.kaipai.module.controller.auth;

import com.kaipai.common.result.R;
import com.kaipai.module.model.auth.dto.LoginReqDTO;
import com.kaipai.module.model.auth.dto.LoginRespDTO;
import com.kaipai.module.model.auth.dto.RegisterReqDTO;
import com.kaipai.module.model.auth.dto.SendCodeReqDTO;
import com.kaipai.module.model.auth.dto.WechatLoginReqDTO;
import com.kaipai.module.server.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证管理", description = "注册 / 登录 / 验证码")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送验证码",
            description = "开发阶段验证码会直接在 data 字段返回，无需真实短信；验证码 5 分钟内有效")
    @PostMapping("/sendCode")
    public R<String> sendCode(@Valid @RequestBody SendCodeReqDTO dto) {
        String code = authService.sendCode(dto.getPhone());
        return R.ok("验证码发送成功", code);
    }

    @Operation(summary = "手机号注册",
            description = "先调用发送验证码接口，再用手机号+验证码+用户类型完成注册，注册成功直接返回 Token")
    @PostMapping("/register")
    public R<LoginRespDTO> register(@Valid @RequestBody RegisterReqDTO dto) {
        return R.ok(authService.register(dto));
    }

    @Operation(summary = "手机号登录",
            description = "先调用发送验证码接口，再用手机号+验证码登录，返回 JWT Token")
    @PostMapping("/login")
    public R<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO dto) {
        return R.ok(authService.login(dto));
    }

    @Operation(summary = "微信手机号一键登录",
            description = "使用微信 getPhoneNumber 返回的 code 换取手机号后登录；若手机号未注册则自动创建账号")
    @PostMapping("/wechat-login")
    public R<LoginRespDTO> wechatLogin(@Valid @RequestBody WechatLoginReqDTO dto) {
        return R.ok(authService.loginByWechat(dto));
    }
}
