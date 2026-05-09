package com.kaipai.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),

    // 认证相关
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    TOKEN_INVALID(401, "Token无效"),

    // 参数相关
    PARAM_ERROR(400, "参数错误"),
    PARAM_NOT_NULL(400, "参数不能为空"),

    // 用户相关
    USER_NOT_FOUND(404, "用户不存在"),
    USER_ALREADY_EXIST(409, "用户已存在"),
    PASSWORD_ERROR(401, "密码错误"),
    ACCOUNT_DISABLED(403, "账号已被禁用"),
    PHONE_ALREADY_BOUND(409, "手机号已被注册"),
    VERIFY_CODE_ERROR(400, "验证码错误或已过期"),

    // 演员相关
    ACTOR_PROFILE_NOT_FOUND(404, "演员档案不存在"),
    ACTOR_PROFILE_ALREADY_EXIST(409, "演员档案已存在"),

    // 剧组相关
    CREW_PROFILE_NOT_FOUND(404, "剧组档案不存在"),
    CREW_NOT_CERTIFIED(403, "剧组尚未认证"),

    // 项目相关
    PROJECT_NOT_FOUND(404, "项目不存在"),
    ROLE_NOT_FOUND(404, "角色不存在"),
    PROJECT_CLOSED(409, "项目已关闭"),

    // 投递相关
    APPLICATION_NOT_FOUND(404, "投递记录不存在"),
    APPLICATION_ALREADY_EXIST(409, "已投递过该角色，请勿重复投递"),
    APPLICATION_STATUS_ERROR(409, "投递状态异常，无法操作"),

    // 文件相关
    FILE_UPLOAD_FAILED(400, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(415, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(413, "文件大小超出限制");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
