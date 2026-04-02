package com.kaipai.module.model.recruit.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleExtraDTO {

    private Long projectId;

    private String coverImage;

    private List<String> tags = new ArrayList<>();
}
