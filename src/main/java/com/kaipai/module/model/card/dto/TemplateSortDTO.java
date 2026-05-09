package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateSortDTO {

    @NotNull
    private Integer sortNo;
}



