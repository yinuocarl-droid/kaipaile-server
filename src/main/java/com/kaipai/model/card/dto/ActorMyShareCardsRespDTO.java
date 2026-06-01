package com.kaipai.model.card.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActorMyShareCardsRespDTO {

    private List<ActorMyShareCardItemDTO> cards;

    private List<ActorSceneTemplateRespDTO> templates;
}



