package com.kaipai.service.ai.profileimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProfileImportSchemaValidator {
    private static final Set<String> PROFILE_FIELDS = Set.of(
            "public_name", "gender", "age", "height", "current_city", "weight", "origin_place",
            "school_name", "major_name", "language_tags", "specialty_tags", "role_type_tags",
            "professional_ability_tags", "intro", "birth_year", "birth_month", "birth_day", "birth_precision");
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidatedExtraction validate(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            List<Candidate> candidates = new ArrayList<>();
            int profileIndex = 0;
            for (JsonNode item : root.path("profileCandidates")) {
                String field = item.path("fieldKey").asText();
                if (!PROFILE_FIELDS.contains(field)) throw new IllegalArgumentException("unknown field: " + field);
                String sourceType = item.path("sourceType").asText("direct");
                boolean inferred = "inferred_from_roles".equals(sourceType);
                String id = item.path("candidateId").asText("profile-" + (++profileIndex));
                candidates.add(new Candidate(id, field, item.path("candidateValue").asText(), sourceType,
                        !inferred, !inferred, inferred));
            }
            List<Work> works = new ArrayList<>();
            int workIndex = 0;
            for (JsonNode item : root.path("workCandidates")) {
                String projectName = text(item, "projectName");
                if (projectName == null || projectName.isBlank()) throw new IllegalArgumentException("work project missing");
                works.add(new Work(item.path("candidateId").asText("work-" + (++workIndex)), projectName,
                        text(item, "roleName"), text(item, "publishStatus"), text(item, "workTypeCode"),
                        text(item, "roleLevelCode"), integer(item, "shootYear"), integer(item, "shootMonth"),
                        text(item, "platform"), text(item, "syncSoundStatus"), strings(item.path("collaborators")),
                        text(item, "achievementText"), text(item, "description"),
                        item.path("sourceType").asText("direct")));
            }
            int placeholders = root.path("ignoredMediaPlaceholderCount").asInt(0);
            return new ValidatedExtraction(candidates, works, placeholders);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid extraction", error);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) node.forEach(value -> { if (!value.asText().isBlank()) result.add(value.asText().trim()); });
        return result;
    }

    public record Candidate(String candidateId, String fieldKey, String value, String sourceType,
            boolean selected, boolean confirmed, boolean requiresExplicitConfirmation) {}
    public record Work(String candidateId, String projectName, String roleName, String publishStatus,
            String workTypeCode, String roleLevelCode, Integer shootYear, Integer shootMonth, String platform,
            String syncSoundStatus, List<String> collaborators, String achievementText, String description,
            String sourceType) {
        public String proofValue() {
            return String.join("|", safe(projectName), safe(roleName), safe(publishStatus), safe(workTypeCode),
                    safe(roleLevelCode), safe(shootYear), safe(shootMonth), safe(platform), safe(syncSoundStatus),
                    String.join(",", collaborators), safe(achievementText), safe(description));
        }
        private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    }
    public record ValidatedExtraction(List<Candidate> profileCandidates, List<Work> workCandidates,
            int ignoredMediaPlaceholderCount) {
        public Candidate profileCandidate(String key) {
            return profileCandidates.stream().filter(candidate -> candidate.fieldKey().equals(key)).findFirst().orElseThrow();
        }
    }
}
