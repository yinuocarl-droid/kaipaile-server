package com.kaipai.model.actor.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ActorProfileRespDTO {
    private Long actorProfileId;
    private Long userId;
    private Integer profileVersion;
    private Long workLibraryVersion;
    private Long avatarAssetId;
    private String publicName;
    private String gender;
    private Integer age;
    private Integer height;
    private String currentCity;
    private Integer weight;
    private String originPlace;
    private String schoolName;
    private String majorName;
    private List<String> languageTags = new ArrayList<>();
    private List<String> specialtyTags = new ArrayList<>();
    private List<String> roleTypeTags = new ArrayList<>();
    private List<String> professionalAbilityTags = new ArrayList<>();
    private String intro;
}
