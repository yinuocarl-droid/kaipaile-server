package com.kaipai.model.actor.dto;

import com.kaipai.model.ai.dto.AiResumeApplyMetaDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActorProfileSaveDTO {

    private String name;

    private String gender;

    private Integer age;

    private Integer height;

    private Integer weight;

    private String city;

    private String birthday;

    private String birthHour;

    private String avatar;

    private String intro;

    private List<String> photos = new ArrayList<>();

    private ActorPhotoCategoriesDTO photoCategories = new ActorPhotoCategoriesDTO();

    private String videoUrl;

    private List<String> skillTypes = new ArrayList<>();

    private List<ActorWorkExperienceDTO> workExperiences = new ArrayList<>();

    private String bodyType;

    private String hairStyle;

    private List<String> languages = new ArrayList<>();

    private String resumePdfUrl;

    private String resumePdfName;

    private Integer resumePdfPageCount;

    private List<String> resumePdfPageImageUrls;

    private String contactPhone;

    private AiResumeApplyMetaDTO aiResumeApplyMeta;
}
