package com.kaipai.model.actor.dto;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.Data;
@Data public class ActorRepresentativeWorksUpdateDTO { @NotNull private List<Long> experienceIds = new ArrayList<>(); }
