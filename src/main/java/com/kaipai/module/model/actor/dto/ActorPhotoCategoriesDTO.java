package com.kaipai.module.model.actor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActorPhotoCategoriesDTO {

    private List<String> portrait = new ArrayList<>();

    private List<String> lifestyle = new ArrayList<>();

    private List<String> production = new ArrayList<>();
}
