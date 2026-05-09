package com.kaipai.module.model.crew.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crew_profile")
public class CrewProfile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long crewProfileId;

    private Long userId;

    private String crewNo;

    private String crewName;

    private String crewShortName;

    /** 主体类型: 1传媒团队, 2剧组, 3选角团队, 4经纪团队 */
    private Integer crewType;

    private String contactName;

    private String contactPhone;

    private String contactWechat;

    private String email;

    private String locationProvince;

    private String locationCity;

    private String address;

    private String logoUrl;

    private String coverUrl;

    private String licenseNo;

    private String licenseUrl;

    private String intro;

    private String businessScope;

    /** 合作标签，逗号分隔 */
    private String cooperationTag;

    /** 是否团队认证 */
    private Boolean isCertified;

    /** 状态: 1正常, 2禁用, 3待审核 */
    private Integer crewStatus;

    private Integer sortNo;

    private String extendedField;
}
