package com.kaipai.model.actor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ActorProfileCareerUpdateDTO {

    @Min(20)
    @Max(300)
    private Integer weight;

    @Size(max = 128)
    private String originPlace;

    @Size(max = 128)
    private String schoolName;

    @Size(max = 128)
    private String majorName;

    private List<String> languageTags = new ArrayList<>();
    private List<String> specialtyTags = new ArrayList<>();
    private List<String> roleTypeTags = new ArrayList<>();
    private List<String> professionalAbilityTags = new ArrayList<>();
}
