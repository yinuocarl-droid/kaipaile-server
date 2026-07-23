package com.kaipai.model.actor.dto;
import java.util.*;
import lombok.Data;
@Data public class ActorWorkRespDTO {
    private Long experienceId; private String projectName; private String publishStatus; private String workTypeCode;
    private String roleLevelCode; private String roleName; private Integer shootYear; private Integer shootMonth;
    private String platform; private String syncSoundStatus; private List<String> collaborators = new ArrayList<>();
    private String achievementText; private String description;
}
