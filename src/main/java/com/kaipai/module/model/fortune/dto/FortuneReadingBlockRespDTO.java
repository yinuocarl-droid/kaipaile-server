package com.kaipai.module.model.fortune.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FortuneReadingBlockRespDTO {

    private String keyword;

    private List<String> readings = new ArrayList<>();
}
