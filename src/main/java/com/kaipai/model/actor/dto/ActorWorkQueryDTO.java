package com.kaipai.model.actor.dto;
import lombok.Data;
@Data public class ActorWorkQueryDTO { private int page = 1; private int size = 10; private String keyword; private String publishStatus; private String workTypeCode; }
