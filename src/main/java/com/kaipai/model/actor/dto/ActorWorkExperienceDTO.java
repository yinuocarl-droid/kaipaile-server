package com.kaipai.model.actor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActorWorkExperienceDTO {

    private Long id;

    private String projectName;

    private String roleName;

    private String shootDate;

    private List<String> photos = new ArrayList<>();

    private String description;
}
