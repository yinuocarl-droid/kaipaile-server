package com.kaipai.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PdfUploadRespDTO {

    private String url;

    private String name;

    private Integer pageCount;

    private List<String> pageImageUrls = new ArrayList<>();
}
