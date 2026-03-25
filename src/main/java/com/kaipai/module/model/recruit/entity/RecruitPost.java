package com.kaipai.module.model.recruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruit_post")
public class RecruitPost extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long recruitPostId;

    private Long userId;

    private Long companyProfileId;

    private String postNo;

    private String title;

    private String dramaName;

    private String roleName;

    private String roleDesc;

    /** 要求性别: 0不限, 1男, 2女 */
    private Integer requireGender;

    private Integer requireAgeMin;

    private Integer requireAgeMax;

    /** 要求最低身高(cm) */
    private Integer requireHeightMin;

    /** 要求最高身高(cm) */
    private Integer requireHeightMax;

    /** 气质要求标签，逗号分隔 */
    private String requireStyleTag;

    private LocalDateTime shootStartTime;

    private LocalDateTime shootEndTime;

    private String shootProvince;

    private String shootCity;

    private String shootAddress;

    private String salary;

    /** 报酬是否公开 */
    private Boolean salaryVisible;

    private String contactName;

    private String contactPhone;

    private String contactWechat;

    private LocalDateTime applyDeadline;

    /** 帖子类型: 1普通, 2紧急置顶 */
    private Integer postType;

    /** 帖子状态: 1招募中, 2已结束, 3已关闭 */
    private Integer postStatus;

    private Integer viewCount;

    private Integer applyCount;

    private String extendedField;
}
