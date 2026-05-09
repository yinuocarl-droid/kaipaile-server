package com.kaipai.module.model.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("share_card_contact_request")
public class ShareCardContactRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long requestId;

    private Long viewerUserId;

    private Long shareCardId;

    private String status;

    private String applicantNote;

    private String decisionNote;

    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;
}



