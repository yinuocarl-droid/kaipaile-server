package com.kaipai.model.actor.dto;

import com.kaipai.common.exception.BizException;

/** Stable numeric failures shared by the 00-199 profile domains. */
public enum ProfileDomainErrorCode {

    PROFILE_IMPORT_DISABLED(46001, "智能导入未启用"),
    PROFILE_IMPORT_UNAVAILABLE(46002, "智能导入暂不可用"),
    PROFILE_IMPORT_INPUT_EMPTY(46003, "导入内容不能为空"),
    PROFILE_IMPORT_INPUT_TOO_LONG(46004, "导入内容超过长度限制"),
    PROFILE_IMPORT_RATE_LIMITED(46005, "智能导入调用次数已达上限"),
    PROFILE_IMPORT_MODEL_TIMEOUT(46006, "智能导入模型响应超时"),
    PROFILE_IMPORT_RESPONSE_INVALID(46007, "智能导入结果无法解析"),
    PROFILE_IMPORT_APPLY_CONFLICT(46008, "智能导入应用冲突"),
    PROFILE_IMPORT_REQUEST_REUSED(46009, "导入请求已被不同内容使用"),
    PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT(46010, "档案或作品库已发生变化"),
    PROFILE_IMPORT_CONFIRMATION_REQUIRED(46011, "推断内容需要明确确认"),
    PROFILE_ASSET_NOT_FOUND(46012, "素材不存在"),
    PROFILE_ASSET_NOT_READY(46013, "素材尚未处理完成"),
    PROFILE_ASSET_IN_USE(46014, "素材正在被引用"),
    PROFILE_WORK_DUPLICATE(46015, "作品已存在"),
    PROFILE_WORK_IN_USE(46016, "作品正在被引用"),
    PROFILE_LEGACY_COLLECTION_WRITE_RETIRED(46017, "旧版作品或素材保存方式已停用，请升级后重试"),
    PROFILE_IMPORT_PROMPT_INVALID(46019, "Prompt 模板或操作参数无效");

    private final int code;
    private final String message;

    ProfileDomainErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String errorCode() {
        return name();
    }

    public String message() {
        return message;
    }

    public BizException toException() {
        return new BizException(code, message);
    }
}
