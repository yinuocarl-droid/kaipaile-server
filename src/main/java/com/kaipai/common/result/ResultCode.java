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
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXIST(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被禁用"),
    PHONE_ALREADY_BOUND(1005, "手机号已被注册"),
    VERIFY_CODE_ERROR(1006, "验证码错误或已过期"),

    // 演员相关
    ACTOR_PROFILE_NOT_FOUND(2001, "演员档案不存在"),
    ACTOR_PROFILE_ALREADY_EXIST(2002, "演员档案已存在"),

    // 剧组相关
    CREW_PROFILE_NOT_FOUND(3001, "剧组档案不存在"),
    CREW_NOT_CERTIFIED(3002, "剧组尚未认证"),

    // 项目相关
    PROJECT_NOT_FOUND(4001, "项目不存在"),
    ROLE_NOT_FOUND(4002, "角色不存在"),
    PROJECT_CLOSED(4003, "项目已关闭"),

    // 投递相关
    APPLICATION_NOT_FOUND(5001, "投递记录不存在"),
    APPLICATION_ALREADY_EXIST(5002, "已投递过该角色，请勿重复投递"),
    APPLICATION_STATUS_ERROR(5003, "投递状态异常，无法操作"),

    // 文件相关
    FILE_UPLOAD_FAILED(6001, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(6002, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(6003, "文件大小超出限制");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
