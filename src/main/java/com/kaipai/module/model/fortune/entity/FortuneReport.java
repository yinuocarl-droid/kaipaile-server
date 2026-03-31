package com.kaipai.module.model.fortune.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fortune_report")
public class FortuneReport extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long fortuneReportId;
    private Long userId;
    private LocalDate reportMonth;
    private String zodiacAnimal;
    private String zodiacFortune;
    private String constellation;
    private String constellationFortune;
    private String ziweiStar;
    private String ziweiProfile;
    private String luckyColor;
    private String luckyColorName;
    private String luckyColorInterpretation;
    private Integer luckyNumber;
    private String luckyNumberInterpretation;
    private String birthHour;
    private String sourceType;
    private String rawPayload;
}
