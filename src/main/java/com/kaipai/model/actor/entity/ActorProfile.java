package com.kaipai.model.actor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_profile")
public class ActorProfile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long actorProfileId;

    private Long userId;

    private String actorNo;

    private String nickName;

    private String realName;

    /** 性别: 1男, 2女, 0未知 */
    private Integer gender;

    private LocalDate birthday;

    /** 出生时辰键值 */
    private String birthHour;

    private Integer age;

    /** 身高(cm) */
    private Integer height;

    /** 体重(kg) */
    private Integer weight;

    private String phone;

    private String wechatNo;

    private String locationProvince;

    private String locationCity;

    private String avatarUrl;

    private Long avatarAssetId;

    private Long currentResumeAssetId;

    private Integer birthYear;

    private Integer birthMonth;

    @TableField("birth_day")
    private Integer birthDayOfMonth;

    private String birthPrecision;

    private String originPlace;

    private String schoolName;

    private String majorName;

    private String languageTagsJson;

    private String specialtyTagsJson;

    private String roleTypeTagsJson;

    private String professionalAbilityTagsJson;

    private Long workLibraryVersion;

    private String coverUrl;

    private String intro;

    /** 技能标签，逗号分隔 */
    private String skillTag;

    /** 风格标签，逗号分隔 */
    private String styleTag;

    private String videoUrl;

    /** 照片集，json数组 */
    private String photoUrls;

    private String experienceDesc;

    /** 是否实名认证 */
    private Boolean isCertified;

    /** 是否开放接单 */
    private Boolean isOpenApply;

    /** 档案状态: 1正常, 2禁用, 3待完善 */
    private Integer profileStatus;

    private Integer sortNo;

    private String extendedField;
}
