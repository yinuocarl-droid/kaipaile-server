package com.kaipai.model.actor.dto;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.Data;
@Data public class ActorWorkSaveDTO {
    @NotBlank @Size(max=255) private String projectName;
    private String publishStatus; private String workTypeCode; private String roleLevelCode;
    @Size(max=255) private String roleName;
    private Integer shootYear; private Integer shootMonth; private String platform; private String syncSoundStatus;
    private List<String> collaborators = new ArrayList<>(); private String achievementText; private String description;
}
