package com.kaipai.model.actor.card.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ActorCardMediaBindingRespDTO {

    private Long setId;
    private Long cardId;
    private Long workId;
    private String usage;
    private List<ActorCardMediaBindingItemRespDTO> items = new ArrayList<>();
}
