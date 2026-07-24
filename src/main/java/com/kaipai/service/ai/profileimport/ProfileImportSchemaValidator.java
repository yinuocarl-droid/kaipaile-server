package com.kaipai.service.ai.profileimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.model.ai.dto.ProfileImportWorkProofValue;
import java.time.LocalDate;
import java.time.Year;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProfileImportSchemaValidator {
    private static final Set<String> PROFILE_FIELDS = Set.of(
            "public_name", "gender", "age", "height", "current_city", "weight", "origin_place",
            "school_name", "major_name", "language_tags", "specialty_tags", "role_type_tags",
            "professional_ability_tags", "intro", "birth_year", "birth_month", "birth_day",
            "birth_precision");
    private static final Set<String> SOURCE_TYPES = Set.of(
            "explicit", "direct", "derived_from_birth", "inferred_from_roles");
    private static final Set<String> PROFILE_TAG_FIELDS = Set.of(
            "language_tags", "specialty_tags", "role_type_tags", "professional_ability_tags");
    private static final int MAX_PROFILE_TAGS = 50;
    private static final int MAX_PROFILE_TAG_LENGTH = 128;
    private static final Set<String> PUBLISH_STATUSES = Set.of(
            "aired", "upcoming", "stage", "horizontal", "other");
    private static final Set<String> WORK_TYPES = Set.of(
            "short_drama", "horizontal_short_drama", "stage_play", "musical",
            "tv_column_drama", "film_tv", "micro_film", "horizontal", "stage", "other");
    private static final Set<String> ROLE_LEVELS = Set.of(
            "lead", "supporting", "antagonist",
            "female_lead", "female_supporting_1", "female_supporting_2", "female_antagonist_1",
            "male_lead", "male_supporting_1", "male_supporting_2", "male_antagonist_1", "other");
    private static final Set<String> SYNC_SOUND_STATUSES = Set.of("sync", "dubbed", "unknown");
    private static final Set<String> WORK_FIELD_KEYS = Set.of(
            "projectName", "roleName", "publishStatus", "workTypeCode", "roleLevelCode",
            "shootYear", "shootMonth", "platform", "syncSoundStatus", "collaborators",
            "achievementText", "description");
    private static final Pattern FEMALE_ROLE_TEXT = Pattern.compile(
            "女主|女一|女二|女三|女反|女性角色|饰演[^\n，,]{0,16}(?:妻|母|姐|妹|女儿)");
    private static final Pattern MALE_ROLE_TEXT = Pattern.compile(
            "男主|男一|男二|男三|男反|男性角色");
    private static final Pattern DIRECT_FEMALE_TEXT = Pattern.compile(
            "(?:性别|本人性别)\s*[：:]?\s*(?:女|女性)(?!主|一|二|三|反)|女演员");
    private static final Pattern DIRECT_MALE_TEXT = Pattern.compile(
            "(?:性别|本人性别)\s*[：:]?\s*(?:男|男性)(?!主|一|二|三|反)|男演员");
    private static final Pattern COMBINED_EVIDENCE_SEPARATOR = Pattern.compile(
            "[/／、,，;；|｜\\r\\n]+");
    private static final String DERIVED_AGE_WARNING = "根据部分生日动态推算";
    private static final Map<String, Set<String>> CONTROLLED_EVIDENCE_TERMS = Map.ofEntries(
            Map.entry("publishStatus:aired", Set.of("已播", "播出", "上线", "上映")),
            Map.entry("publishStatus:upcoming", Set.of("待播", "待上线", "待上映")),
            Map.entry("publishStatus:stage", Set.of("舞台", "话剧", "音乐剧")),
            Map.entry("publishStatus:horizontal", Set.of("横屏")),
            Map.entry("publishStatus:other", Set.of("其他")),
            Map.entry("workTypeCode:short_drama", Set.of("短剧", "微短剧")),
            Map.entry("workTypeCode:horizontal_short_drama", Set.of("横屏短剧", "横屏")),
            Map.entry("workTypeCode:stage_play", Set.of("舞台剧", "话剧")),
            Map.entry("workTypeCode:musical", Set.of("音乐剧")),
            Map.entry("workTypeCode:tv_column_drama", Set.of("栏目剧")),
            Map.entry("workTypeCode:film_tv", Set.of("影视", "电影", "电视剧")),
            Map.entry("workTypeCode:micro_film", Set.of("微电影")),
            Map.entry("workTypeCode:horizontal", Set.of("横屏")),
            Map.entry("workTypeCode:stage", Set.of("舞台")),
            Map.entry("workTypeCode:other", Set.of("其他")),
            Map.entry("roleLevelCode:lead", Set.of("主演", "主角")),
            Map.entry("roleLevelCode:supporting", Set.of("配角")),
            Map.entry("roleLevelCode:antagonist", Set.of("反派")),
            Map.entry("roleLevelCode:female_lead", Set.of("女主", "女一")),
            Map.entry("roleLevelCode:female_supporting_1", Set.of("女配一", "女一配")),
            Map.entry("roleLevelCode:female_supporting_2", Set.of("女二", "女配二")),
            Map.entry("roleLevelCode:female_antagonist_1", Set.of("女反一", "女反")),
            Map.entry("roleLevelCode:male_lead", Set.of("男主", "男一")),
            Map.entry("roleLevelCode:male_supporting_1", Set.of("男配一", "男一配")),
            Map.entry("roleLevelCode:male_supporting_2", Set.of("男二", "男配二")),
            Map.entry("roleLevelCode:male_antagonist_1", Set.of("男反一", "男反")),
            Map.entry("roleLevelCode:other", Set.of("其他")),
            Map.entry("syncSoundStatus:sync", Set.of("同期声", "同期录音")),
            Map.entry("syncSoundStatus:dubbed", Set.of("配音", "后期配音")),
            Map.entry("syncSoundStatus:unknown", Set.of("未知", "不详")));

    private final ObjectMapper mapper = new ObjectMapper();

    public ValidatedExtraction validate(String json) {
        return validate(json, null);
    }

    public ValidatedExtraction validate(String json, String rawText) {
        try {
            JsonNode root = mapper.readTree(json);
            require(root != null && root.isObject(), "extraction root must be an object");
            require(root.path("profileCandidates").isArray(), "profileCandidates must be an array");
            require(root.path("workCandidates").isArray(), "workCandidates must be an array");

            List<Work> works = parseWorks(root.path("workCandidates"), rawText);
            List<Candidate> candidates = parseProfileCandidates(root.path("profileCandidates"), rawText);
            validateGenderInference(candidates, works, rawText);
            validateBirthPrecision(candidates);
            addDerivedAgeCandidate(candidates);
            int placeholders = StringUtils.hasText(rawText)
                    ? Math.toIntExact(Pattern.compile("\\[(?:图片|视频)]").matcher(rawText).results().count())
                    : nonNegativeInteger(root, "ignoredMediaPlaceholderCount", 0);
            return new ValidatedExtraction(
                    candidates, works, placeholders, strings(root.path("unmappedSegments")),
                    strings(root.path("warnings")));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid extraction", error);
        }
    }

    public void validateProfileFinalValue(String field, String value) {
        require(PROFILE_FIELDS.contains(field), "unknown field: " + field);
        require(value != null, "profile final value missing");
        validateProfileValue(field, value.trim());
    }

    private List<Candidate> parseProfileCandidates(JsonNode array, String rawText) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int profileIndex = 0;
        for (JsonNode item : array) {
            require(item.isObject(), "profile candidate must be an object");
            String field = requiredText(item, "fieldKey");
            require(PROFILE_FIELDS.contains(field), "unknown field: " + field);
            String sourceType = optionalText(item, "sourceType", "direct");
            require(SOURCE_TYPES.contains(sourceType), "invalid profile source type");
            if ("derived_from_birth".equals(sourceType)) {
                require("age".equals(field), "derived birth source is only valid for age");
                continue;
            }
            if ("inferred_from_roles".equals(sourceType)) {
                require("gender".equals(field), "role inference is only valid for gender");
            }
            String value = requiredValueText(item, "candidateValue");
            validateProfileValue(field, value);
            Double confidence = confidence(item);
            String sourceText = text(item, "sourceText");
            validateEvidence(sourceText, rawText, "inferred_from_roles".equals(sourceType));
            validateProfileEvidence(field, value, sourceText, sourceType);
            if ("current_city".equals(field) && sourceText != null
                    && Pattern.compile("籍贯|祖籍|出生地").matcher(sourceText).find()) {
                throw new IllegalArgumentException("origin evidence cannot populate current city");
            }
            String id = optionalText(item, "candidateId", "profile-" + (++profileIndex));
            require(ids.add(id), "duplicate profile candidate id");
            boolean inferred = "inferred_from_roles".equals(sourceType);
            candidates.add(new Candidate(
                    id, field, value, confidence, sourceText, sourceType, text(item, "warning"),
                    !inferred, !inferred, inferred));
        }
        return candidates;
    }

    private List<Work> parseWorks(JsonNode array, String rawText) {
        List<Work> works = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int workIndex = 0;
        for (JsonNode item : array) {
            require(item.isObject(), "work candidate must be an object");
            String projectName = text(item, "projectName");
            require(StringUtils.hasText(projectName), "work project missing");
            require(projectName.length() <= 255, "work project too long");
            String id = optionalText(item, "candidateId", "work-" + (++workIndex));
            require(ids.add(id), "duplicate work candidate id");
            String publishStatus = text(item, "publishStatus");
            String workTypeCode = text(item, "workTypeCode");
            String roleLevelCode = text(item, "roleLevelCode");
            String syncSoundStatus = text(item, "syncSoundStatus");
            requireAllowed(publishStatus, PUBLISH_STATUSES, "publishStatus");
            requireAllowed(workTypeCode, WORK_TYPES, "workTypeCode");
            requireAllowed(roleLevelCode, ROLE_LEVELS, "roleLevelCode");
            requireAllowed(syncSoundStatus, SYNC_SOUND_STATUSES, "syncSoundStatus");
            Integer shootYear = integer(item, "shootYear");
            Integer shootMonth = integer(item, "shootMonth");
            if (shootYear != null) require(shootYear >= 1900 && shootYear <= Year.now().getValue() + 5, "invalid shootYear");
            if (shootMonth != null) require(shootMonth >= 1 && shootMonth <= 12, "invalid shootMonth");
            List<String> collaborators = strings(item.path("collaborators"));
            require(collaborators.size() <= 50, "too many collaborators");
            Map<String, FieldEvidence> fields = parseWorkFields(item.path("fields"), rawText);
            validateWorkFieldConsistency(item, fields);
            validateWorkEvidenceCompleteness(item, fields);
            String sourceType = optionalText(item, "sourceType", "explicit");
            require(Set.of("explicit", "direct").contains(sourceType), "invalid work source type");
            works.add(new Work(
                    id, projectName, text(item, "roleName"), publishStatus, workTypeCode, roleLevelCode,
                    shootYear, shootMonth, text(item, "platform"), syncSoundStatus, collaborators,
                    text(item, "achievementText"), text(item, "description"), sourceType, fields));
        }
        return works;
    }

    private Map<String, FieldEvidence> parseWorkFields(JsonNode node, String rawText) {
        Map<String, FieldEvidence> fields = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) return fields;
        require(node.isObject(), "work fields must be an object");
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode evidence = entry.getValue();
            require(WORK_FIELD_KEYS.contains(key), "unknown work field: " + key);
            require(evidence.isObject(), "work field evidence must be an object");
            JsonNode candidateValue = evidence.get("candidateValue");
            require(candidateValue != null && !candidateValue.isNull(), "work field candidate value missing");
            Double confidence = confidence(evidence);
            String sourceText = text(evidence, "sourceText");
            validateEvidence(sourceText, rawText, false);
            String sourceType = optionalText(evidence, "sourceType", "explicit");
            require(SOURCE_TYPES.contains(sourceType), "invalid work evidence source type");
            fields.put(key, new FieldEvidence(
                    mapper.convertValue(candidateValue, Object.class), confidence, sourceText, sourceType,
                    text(evidence, "warning")));
        });
        return fields;
    }

    private void validateWorkFieldConsistency(JsonNode item, Map<String, FieldEvidence> fields) {
        for (Map.Entry<String, FieldEvidence> entry : fields.entrySet()) {
            JsonNode flat = item.get(entry.getKey());
            if (flat == null || flat.isNull()) continue;
            Object flatValue = mapper.convertValue(flat, Object.class);
            require(canonical(flatValue).equals(canonical(entry.getValue().candidateValue())),
                    "work field evidence differs from candidate value");
        }
    }

    private void validateWorkEvidenceCompleteness(JsonNode item, Map<String, FieldEvidence> fields) {
        for (String field : WORK_FIELD_KEYS) {
            JsonNode value = item.get(field);
            if (value == null || value.isNull() || value.isTextual() && !StringUtils.hasText(value.asText())
                    || value.isArray() && value.isEmpty()) continue;
            require(fields.containsKey(field), "work field evidence missing: " + field);
            FieldEvidence evidence = fields.get(field);
            require(evidenceSupports(field, value, evidence.sourceText()),
                    "work fact differs from source evidence: " + field);
        }
    }

    private boolean evidenceSupports(String field, JsonNode value, String sourceText) {
        Set<String> controlledTerms = CONTROLLED_EVIDENCE_TERMS.get(
                field + ":" + (value.isTextual() ? value.asText() : ""));
        if (controlledTerms != null) {
            String evidence = normalize(sourceText);
            return controlledTerms.stream().map(this::normalize).anyMatch(evidence::contains);
        }
        String normalizedEvidence = normalize(sourceText);
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item.isTextual() && !normalizedEvidence.contains(normalize(item.asText()))) return false;
            }
            return true;
        }
        if (value.isNumber()) return containsNumber(sourceText, value.asText());
        return normalizedEvidence.contains(normalize(value.asText()));
    }

    private void validateProfileEvidence(
            String field, String value, String sourceText, String sourceType) {
        if ("inferred_from_roles".equals(sourceType)) return;
        if ("gender".equals(field)) {
            Pattern directPattern = "female".equals(value) ? DIRECT_FEMALE_TEXT : DIRECT_MALE_TEXT;
            require(directPattern.matcher(sourceText).find(), "explicit gender requires direct self description");
            return;
        }
        if ("birth_precision".equals(field)) {
            require(birthPrecisionIsSupported(value, sourceText), "birth precision differs from source evidence");
            return;
        }
        if (Set.of("age", "height", "weight", "birth_year", "birth_month", "birth_day")
                .contains(field)) {
            require(containsNumber(sourceText, value), "profile number differs from source evidence: " + field);
            return;
        }
        if (Set.of("language_tags", "specialty_tags", "role_type_tags",
                "professional_ability_tags").contains(field)) {
            try {
                JsonNode tags = mapper.readTree(value);
                require(tags.isArray() && evidenceSupports(field, tags, sourceText),
                        "profile tags differ from source evidence: " + field);
            } catch (Exception error) {
                throw new IllegalArgumentException("invalid profile tags", error);
            }
            return;
        }
        require(normalize(sourceText).contains(normalize(value)),
                "profile fact differs from source evidence: " + field);
    }

    private boolean birthPrecisionIsSupported(String precision, String sourceText) {
        int count = 0;
        var matcher = Pattern.compile("(?<!\\d)\\d{1,4}(?!\\d)").matcher(sourceText);
        while (matcher.find()) count++;
        return switch (precision) {
            case "year" -> count >= 1;
            case "month" -> count >= 2;
            case "day" -> count >= 3;
            default -> false;
        };
    }

    private boolean containsNumber(String sourceText, String value) {
        return Pattern.compile("(?<!\\d)" + Pattern.quote(value) + "(?!\\d)")
                .matcher(sourceText).find();
    }

    private void validateGenderInference(List<Candidate> candidates, List<Work> works, String rawText) {
        for (Candidate candidate : candidates) {
            if (!"gender".equals(candidate.fieldKey())
                    || !"inferred_from_roles".equals(candidate.sourceType())) continue;
            require("female".equals(candidate.value()), "unsupported inferred gender");
            long femaleWorks = works.stream()
                    .filter(work -> work.roleLevelCode() != null && work.roleLevelCode().startsWith("female_"))
                    .map(work -> work.projectName() + "\u0000" + work.roleName())
                    .distinct()
                    .count();
            boolean maleWork = works.stream().anyMatch(
                    work -> work.roleLevelCode() != null && work.roleLevelCode().startsWith("male_"));
            String source = rawText == null ? "" : rawText;
            long femaleTextSignals = FEMALE_ROLE_TEXT.matcher(source).results()
                    .count();
            boolean maleTextSignal = MALE_ROLE_TEXT.matcher(source).find();
            require(femaleWorks >= 2 && femaleTextSignals >= 2 && !maleWork && !maleTextSignal,
                    "insufficient or contradictory gender role evidence");
        }
    }

    private void validateBirthPrecision(List<Candidate> candidates) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Candidate candidate : candidates) values.put(candidate.fieldKey(), candidate.value());
        String precision = values.get("birth_precision");
        if (precision == null) return;
        Integer year = parseNullableInteger(values.get("birth_year"));
        Integer month = parseNullableInteger(values.get("birth_month"));
        Integer day = parseNullableInteger(values.get("birth_day"));
        switch (precision) {
            case "year" -> require(year != null && month == null && day == null, "invalid year precision birthday");
            case "month" -> require(year != null && month != null && day == null, "invalid month precision birthday");
            case "day" -> {
                require(year != null && month != null && day != null, "invalid day precision birthday");
                LocalDate.of(year, month, day);
            }
            default -> throw new IllegalArgumentException("invalid birth precision");
        }
    }

    private void addDerivedAgeCandidate(List<Candidate> candidates) {
        Map<String, Candidate> byField = new LinkedHashMap<>();
        for (Candidate candidate : candidates) byField.put(candidate.fieldKey(), candidate);
        if (byField.containsKey("age")) return;

        Candidate precisionCandidate = byField.get("birth_precision");
        if (precisionCandidate == null) return;
        Candidate yearCandidate = byField.get("birth_year");
        Candidate monthCandidate = byField.get("birth_month");
        Candidate dayCandidate = byField.get("birth_day");
        int year = Integer.parseInt(yearCandidate.value());
        Integer month = monthCandidate == null ? null : Integer.valueOf(monthCandidate.value());
        Integer day = dayCandidate == null ? null : Integer.valueOf(dayCandidate.value());

        LocalDate today = LocalDate.now();
        int age = today.getYear() - year;
        if (month != null && (today.getMonthValue() < month
                || day != null && today.getMonthValue() == month && today.getDayOfMonth() < day)) {
            age--;
        }
        String value = Integer.toString(age);
        validateProfileValue("age", value);

        List<Candidate> evidenceCandidates = new ArrayList<>();
        evidenceCandidates.add(yearCandidate);
        if (monthCandidate != null) evidenceCandidates.add(monthCandidate);
        if (dayCandidate != null) evidenceCandidates.add(dayCandidate);
        evidenceCandidates.add(precisionCandidate);
        double confidence = evidenceCandidates.stream()
                .mapToDouble(Candidate::confidence)
                .min()
                .orElse(1d);
        String sourceText = evidenceCandidates.stream()
                .map(Candidate::sourceText)
                .distinct()
                .reduce((left, right) -> left + " / " + right)
                .orElse(precisionCandidate.sourceText());
        Set<String> candidateIds = new LinkedHashSet<>();
        candidates.forEach(candidate -> candidateIds.add(candidate.candidateId()));
        String candidateId = "profile-derived-age";
        int suffix = 2;
        while (candidateIds.contains(candidateId)) candidateId = "profile-derived-age-" + suffix++;
        candidates.add(new Candidate(
                candidateId, "age", value, confidence, sourceText, "derived_from_birth",
                DERIVED_AGE_WARNING, false, false, false));
    }

    private void validateProfileValue(String field, String value) {
        switch (field) {
            case "gender" -> require(Set.of("male", "female").contains(value), "invalid gender");
            case "age" -> requireRange(value, 1, 120, field);
            case "height" -> requireRange(value, 50, 250, field);
            case "weight" -> requireRange(value, 20, 300, field);
            case "birth_year" -> requireRange(value, 1900, Year.now().getValue(), field);
            case "birth_month" -> requireRange(value, 1, 12, field);
            case "birth_day" -> requireRange(value, 1, 31, field);
            case "birth_precision" -> require(Set.of("year", "month", "day").contains(value),
                    "invalid birth precision");
            case "public_name", "current_city" -> {
                require(StringUtils.hasText(value), field + " missing");
                require(value.length() <= 64, "profile value too long");
            }
            case "origin_place", "school_name", "major_name" ->
                    require(value.length() <= 128, "profile value too long");
            case "intro" -> require(value.length() <= 2000, "profile value too long");
            default -> {
                require(PROFILE_TAG_FIELDS.contains(field), "unknown field: " + field);
                validateProfileTags(value);
            }
        }
    }

    private void validateProfileTags(String value) {
        try {
            JsonNode tags = mapper.readTree(value);
            require(tags != null && tags.isArray(), "profile tags must be an array");
            require(tags.size() <= MAX_PROFILE_TAGS, "too many profile tags");
            for (JsonNode tag : tags) {
                require(tag.isTextual(), "profile tags contain a non-string");
                String text = tag.asText().trim();
                require(StringUtils.hasText(text), "profile tag must not be blank");
                require(text.length() <= MAX_PROFILE_TAG_LENGTH, "profile tag too long");
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid profile tags", error);
        }
    }

    private void validateEvidence(String sourceText, String rawText, boolean combinedRoleEvidence) {
        require(StringUtils.hasText(sourceText), "candidate source evidence missing");
        if (!StringUtils.hasText(rawText)) return;
        String normalizedInput = normalize(rawText);
        if (combinedRoleEvidence) {
            for (String fragment : COMBINED_EVIDENCE_SEPARATOR.split(sourceText)) {
                if (StringUtils.hasText(fragment)) {
                    require(normalizedInput.contains(normalize(fragment)),
                            "candidate source evidence not found in input");
                }
            }
            return;
        }
        require(normalizedInput.contains(normalize(sourceText)), "candidate source evidence not found in input");
    }

    private Double confidence(JsonNode item) {
        JsonNode value = item.get("confidence");
        require(value != null && value.isNumber(), "candidate confidence missing");
        double confidence = value.asDouble();
        require(Double.isFinite(confidence) && confidence >= 0d && confidence <= 1d, "invalid confidence");
        return confidence;
    }

    private void requireRange(String value, int min, int max, String field) {
        Integer number = parseNullableInteger(value);
        require(number != null && number >= min && number <= max, "invalid " + field);
    }

    private void requireAllowed(String value, Set<String> allowed, String field) {
        if (value != null) require(allowed.contains(value), "invalid " + field);
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        require(StringUtils.hasText(value), field + " missing");
        return value;
    }

    private String requiredValueText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        require(value != null && !value.isNull() && (value.isValueNode() || value.isArray()), field + " missing");
        if (value.isTextual()) return value.asText().trim();
        try {
            return value.isArray() ? mapper.writeValueAsString(value) : value.asText();
        } catch (Exception error) {
            throw new IllegalArgumentException(field + " invalid", error);
        }
    }

    private String optionalText(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        require(value.isIntegralNumber(), field + " must be an integer");
        return value.intValue();
    }

    private int nonNegativeInteger(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        require(value.isIntegralNumber() && value.intValue() >= 0, field + " must be non-negative");
        return value.intValue();
    }

    private List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) return result;
        require(node.isArray(), "string collection must be an array");
        node.forEach(value -> {
            require(value.isTextual(), "string collection contains a non-string");
            if (StringUtils.hasText(value.asText())) result.add(value.asText().trim());
        });
        return result;
    }

    private Integer parseNullableInteger(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid integer", error);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\s《》〈〉「」『』【】()（）,，。:：;；]", "")
                .toLowerCase(java.util.Locale.ROOT)
                .trim();
    }

    private String canonical(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid candidate value", error);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public record Candidate(
            String candidateId, String fieldKey, String value, Double confidence, String sourceText,
            String sourceType, String warning, boolean selected, boolean confirmed,
            boolean requiresExplicitConfirmation) {
    }

    public record FieldEvidence(
            Object candidateValue, Double confidence, String sourceText, String sourceType, String warning) {
    }

    public record Work(
            String candidateId, String projectName, String roleName, String publishStatus,
            String workTypeCode, String roleLevelCode, Integer shootYear, Integer shootMonth, String platform,
            String syncSoundStatus, List<String> collaborators, String achievementText, String description,
            String sourceType, Map<String, FieldEvidence> fields) {
        public ProfileImportWorkProofValue proofValue() {
            return new ProfileImportWorkProofValue(
                    projectName, roleName, publishStatus, workTypeCode, roleLevelCode,
                    shootYear, shootMonth, platform, syncSoundStatus, collaborators,
                    achievementText, description);
        }
    }

    public record ValidatedExtraction(
            List<Candidate> profileCandidates, List<Work> workCandidates,
            int ignoredMediaPlaceholderCount, List<String> unmappedSegments, List<String> warnings) {
        public Candidate profileCandidate(String key) {
            return profileCandidates.stream()
                    .filter(candidate -> candidate.fieldKey().equals(key))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
