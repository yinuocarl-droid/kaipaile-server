package com.kaipai.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "lastUpdate", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateUserId", Long.class, null);
        this.strictInsertFill(metaObject, "updateUserName", String.class, "");
        this.strictInsertFill(metaObject, "createUserId", Long.class, null);
        this.strictInsertFill(metaObject, "createUserName", String.class, "");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "lastUpdate", LocalDateTime.class, LocalDateTime.now());
    }
}
