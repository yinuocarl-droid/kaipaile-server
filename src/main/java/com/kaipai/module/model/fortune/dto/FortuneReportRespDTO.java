package com.kaipai.module.model.fortune.dto;

import lombok.Data;

@Data
public class FortuneReportRespDTO {

    private String month;

    private String zodiacAnimal;

    private FortuneReadingBlockRespDTO zodiacFortune = new FortuneReadingBlockRespDTO();

    private String constellation;

    private FortuneReadingBlockRespDTO constellationFortune = new FortuneReadingBlockRespDTO();

    private String ziweiStar;

    private ZiweiProfileRespDTO ziweiProfile;

    private String luckyColor;

    private String luckyColorName;

    private String luckyColorInterpretation;

    private Integer luckyNumber;

    private String luckyNumberInterpretation;
}
