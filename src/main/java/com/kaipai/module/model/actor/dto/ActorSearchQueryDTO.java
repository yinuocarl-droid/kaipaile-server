package com.kaipai.module.model.actor.dto;

import lombok.Data;

@Data
public class ActorSearchQueryDTO {

    private long page = 1;

    private long size = 10;

    private String gender;

    private Integer minAge;

    private Integer maxAge;

    private String city;

    private String skillType;

    private String keyword;
}
