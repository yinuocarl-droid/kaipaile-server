package com.kaipai.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private String rid;

    @TableField(fill = FieldFill.INSERT)
    private Long createUserId;

    @TableField(fill = FieldFill.INSERT)
    private String createUserName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUserId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateUserName;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdate;
}
