package com.kaipai.model.actor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActorProfileCoreUpdateDTO {

    @NotBlank
    @Size(max = 64)
    private String publicName;

    @NotBlank
    @Pattern(regexp = "male|female")
    private String gender;

    @NotNull
    @Min(1)
    @Max(120)
    private Integer age;

    @NotNull
    @Min(50)
    @Max(250)
    private Integer height;

    @NotBlank
    @Size(max = 64)
    private String currentCity;
}
