package com.kaipai.model.card.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ContactRequestStatusQueryDTO {

    @NotNull(message = "shareCardId 不能为空")
    private Long shareCardId;
}



