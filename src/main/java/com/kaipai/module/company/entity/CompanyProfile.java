package com.kaipai.module.company.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("company_profile")
public class CompanyProfile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long companyProfileId;

    private Long userId;

    private String companyNo;

    private String companyName;

    private String companyShortName;

    /** 主体类型: 1传媒公司, 2剧组, 3选角团队, 4经纪公司 */
    private Integer companyType;

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

    /** 是否企业认证 */
    private Boolean isCertified;

    /** 状态: 1正常, 2禁用, 3待审核 */
    private Integer companyStatus;

    private Integer sortNo;

    private String extendedField;
}
