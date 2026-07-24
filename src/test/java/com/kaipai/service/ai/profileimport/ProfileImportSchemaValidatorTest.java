package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator.Candidate;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator.ValidatedExtraction;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProfileImportSchemaValidatorTest {

    private final ProfileImportSchemaValidator validator = new ProfileImportSchemaValidator();

    @Test
    void twoIndependentFemaleRoleSignalsCreateUnselectedConfirmableGender() {
        String rawText = "《作品一》女主 林一\n《作品二》女二 林二";

        ValidatedExtraction result = validator.validate("""
                {
                  "profileCandidates": [{
                    "candidateId": "gender-1",
                    "fieldKey": "gender",
                    "candidateValue": "female",
                    "confidence": 0.86,
                    "sourceText": "女主 / 女二",
                    "sourceType": "inferred_from_roles",
                    "warning": "根据多条作品角色推断，请确认"
                  }],
                  "workCandidates": [
                    {
                      "candidateId": "work-1", "projectName": "作品一", "roleName": "林一", "roleLevelCode": "female_lead",
                      "fields": {
                        "projectName": {"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》女主 林一","sourceType":"explicit"},
                        "roleName": {"candidateValue":"林一","confidence":0.99,"sourceText":"《作品一》女主 林一","sourceType":"explicit"},
                        "roleLevelCode": {"candidateValue":"female_lead","confidence":0.99,"sourceText":"女主","sourceType":"explicit"}
                      }
                    },
                    {
                      "candidateId": "work-2", "projectName": "作品二", "roleName": "林二", "roleLevelCode": "female_supporting_2",
                      "fields": {
                        "projectName": {"candidateValue":"作品二","confidence":0.99,"sourceText":"《作品二》女二 林二","sourceType":"explicit"},
                        "roleName": {"candidateValue":"林二","confidence":0.99,"sourceText":"《作品二》女二 林二","sourceType":"explicit"},
                        "roleLevelCode": {"candidateValue":"female_supporting_2","confidence":0.99,"sourceText":"女二","sourceType":"explicit"}
                      }
                    }
                  ]
                }
                """, rawText);

        Candidate gender = result.profileCandidate("gender");
        assertEquals("female", gender.value());
        assertEquals(0.86d, gender.confidence());
        assertEquals("女主 / 女二", gender.sourceText());
        assertEquals("根据多条作品角色推断，请确认", gender.warning());
        assertFalse(gender.selected());
        assertFalse(gender.confirmed());
        assertTrue(gender.requiresExplicitConfirmation());
    }

    @Test
    void twoIndependentFemaleLeadWorksAlsoAuthorizeConfirmableGender() {
        String rawText = "《作品一》女主 林一\n《作品二》女主 林二";
        String json = """
                {
                  "profileCandidates": [{
                    "candidateId":"gender-1","fieldKey":"gender","candidateValue":"female",
                    "confidence":0.86,"sourceText":"女主 / 女主","sourceType":"inferred_from_roles"
                  }],
                  "workCandidates": [
                    {"candidateId":"work-1","projectName":"作品一","roleLevelCode":"female_lead","fields":{
                      "projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》女主 林一","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_lead","confidence":0.99,"sourceText":"女主","sourceType":"explicit"}
                    }},
                    {"candidateId":"work-2","projectName":"作品二","roleLevelCode":"female_lead","fields":{
                      "projectName":{"candidateValue":"作品二","confidence":0.99,"sourceText":"《作品二》女主 林二","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_lead","confidence":0.99,"sourceText":"女主","sourceType":"explicit"}
                    }}
                  ]
                }
                """;

        Candidate gender = validator.validate(json, rawText).profileCandidate("gender");

        assertTrue(gender.requiresExplicitConfirmation());
        assertFalse(gender.selected());
    }

    @Test
    void singleFemaleRoleSignalCannotAuthorizeGenderInference() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [{
                    "fieldKey": "gender",
                    "candidateValue": "female",
                    "confidence": 0.9,
                    "sourceText": "女主",
                    "sourceType": "inferred_from_roles"
                  }],
                  "workCandidates": [
                    {"candidateId": "work-1", "projectName": "作品一", "roleLevelCode": "female_lead"}
                  ]
                }
                """, "《作品一》女主"));
    }

    @Test
    void contradictoryRoleSignalsCannotAuthorizeGenderInference() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [{
                    "fieldKey": "gender",
                    "candidateValue": "female",
                    "confidence": 0.9,
                    "sourceText": "女主 / 女二 / 男主",
                    "sourceType": "inferred_from_roles"
                  }],
                  "workCandidates": [
                    {"candidateId": "work-1", "projectName": "作品一", "roleLevelCode": "female_lead"},
                    {"candidateId": "work-2", "projectName": "作品二", "roleLevelCode": "female_supporting_2"},
                    {"candidateId": "work-3", "projectName": "作品三", "roleLevelCode": "male_lead"}
                  ]
                }
                """, "《作品一》女主\n《作品二》女二\n《作品三》男主"));
    }

    @Test
    void inferredGenderRejectsAnySourceFragmentMissingFromTheInput() {
        String rawText = "《作品一》女主 林一\n《作品二》女二 林二";

        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [{
                    "candidateId":"gender-1","fieldKey":"gender","candidateValue":"female",
                    "confidence":0.86,"sourceText":"女主 / 女二 / 伪造女反一","sourceType":"inferred_from_roles"
                  }],
                  "workCandidates": [
                    {"candidateId":"work-1","projectName":"作品一","roleName":"林一","roleLevelCode":"female_lead","fields":{
                      "projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》女主 林一","sourceType":"explicit"},
                      "roleName":{"candidateValue":"林一","confidence":0.99,"sourceText":"《作品一》女主 林一","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_lead","confidence":0.99,"sourceText":"女主","sourceType":"explicit"}
                    }},
                    {"candidateId":"work-2","projectName":"作品二","roleName":"林二","roleLevelCode":"female_supporting_2","fields":{
                      "projectName":{"candidateValue":"作品二","confidence":0.99,"sourceText":"《作品二》女二 林二","sourceType":"explicit"},
                      "roleName":{"candidateValue":"林二","confidence":0.99,"sourceText":"《作品二》女二 林二","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_supporting_2","confidence":0.99,"sourceText":"女二","sourceType":"explicit"}
                    }}
                  ]
                }
                """, rawText));
    }

    @Test
    void inferredFromRolesCannotAuthorizeNonGenderProfileFields() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [{
                    "fieldKey": "height",
                    "candidateValue": "170",
                    "confidence": 0.9,
                    "sourceText": "女主 / 女二",
                    "sourceType": "inferred_from_roles"
                  }],
                  "workCandidates": []
                }
                """, "170cm，曾出演女主 / 女二"));
    }

    @Test
    void originEvidenceCannotPopulateCurrentCity() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [{
                    "fieldKey": "current_city",
                    "candidateValue": "中国香港",
                    "confidence": 0.99,
                    "sourceText": "籍贯：中国香港",
                    "sourceType": "explicit"
                  }],
                  "workCandidates": []
                }
                """, "籍贯：中国香港"));
    }

    @Test
    void monthPrecisionBirthdayDoesNotInventADay() {
        ValidatedExtraction result = validator.validate("""
                {
                  "profileCandidates": [
                    {"fieldKey": "birth_year", "candidateValue": "2004", "confidence": 0.99, "sourceText": "生日：2004.9", "sourceType": "explicit"},
                    {"fieldKey": "birth_month", "candidateValue": "9", "confidence": 0.99, "sourceText": "生日：2004.9", "sourceType": "explicit"},
                    {"fieldKey": "birth_precision", "candidateValue": "month", "confidence": 0.99, "sourceText": "生日：2004.9", "sourceType": "explicit"}
                  ],
                  "workCandidates": []
                }
                """, "生日：2004.9");

        assertEquals("2004", result.profileCandidate("birth_year").value());
        assertEquals("9", result.profileCandidate("birth_month").value());
        assertEquals("month", result.profileCandidate("birth_precision").value());
        Candidate age = result.profileCandidate("age");
        LocalDate today = LocalDate.now();
        int expectedAge = today.getYear() - 2004 - (today.getMonthValue() < 9 ? 1 : 0);
        assertEquals(Integer.toString(expectedAge), age.value());
        assertEquals("derived_from_birth", age.sourceType());
        assertEquals("根据部分生日动态推算", age.warning());
        assertFalse(age.selected());
        assertFalse(age.confirmed());
        assertNull(result.profileCandidates().stream()
                .filter(candidate -> candidate.fieldKey().equals("birth_day"))
                .findFirst().orElse(null));
    }

    @Test
    void modelOnlyDerivedAgeIsNotAuthoritativeWithoutValidatedBirthdayCandidates() {
        ValidatedExtraction result = validator.validate("""
                {
                  "profileCandidates": [
                    {"candidateId":"model-age","fieldKey":"age","candidateValue":"21","confidence":0.99,"sourceText":"生日：2004.9","sourceType":"derived_from_birth"}
                  ],
                  "workCandidates": []
                }
                """, "生日：2004.9");

        assertTrue(result.profileCandidates().stream()
                .noneMatch(candidate -> candidate.fieldKey().equals("age")));
    }

    @Test
    void invalidProfileAndWorkEnumsOrNumbersAreRejected() {
        assertInvalid(profileCandidate("birth_month", "13"));
        assertInvalid(profileCandidate("height", "20"));
        assertInvalid(workCandidate("publishStatus", "invented"));
        assertInvalid(workCandidate("shootMonth", 14));
        assertInvalid(workCandidate("shootYear", 1800));
    }

    @Test
    void candidateAndPerWorkFieldEvidenceAreRetained() {
        ValidatedExtraction result = validator.validate("""
                {
                  "profileCandidates": [{
                    "candidateId": "height-1",
                    "fieldKey": "height",
                    "candidateValue": "170",
                    "confidence": 0.98,
                    "sourceText": "170/45kg",
                    "sourceType": "explicit",
                    "warning": null
                  }],
                  "workCandidates": [{
                    "candidateId": "work-1",
                    "projectName": "作品一",
                    "fields": {
                      "projectName": {
                        "candidateValue": "作品一",
                        "confidence": 0.99,
                        "sourceText": "《作品一》女主 林一",
                        "sourceType": "explicit",
                        "warning": null
                      }
                    }
                  }]
                }
                """, "170/45kg\n《作品一》女主 林一");

        Candidate height = result.profileCandidate("height");
        assertEquals(0.98d, height.confidence());
        assertEquals("170/45kg", height.sourceText());
        assertNull(height.warning());
        var projectEvidence = result.workCandidates().get(0).fields().get("projectName");
        assertEquals("作品一", projectEvidence.candidateValue());
        assertEquals(0.99d, projectEvidence.confidence());
        assertEquals("《作品一》女主 林一", projectEvidence.sourceText());
        assertEquals("explicit", projectEvidence.sourceType());
    }

    @Test
    void everyNonNullWorkFieldRequiresEvidence() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [],
                  "workCandidates": [{
                    "candidateId": "work-1",
                    "projectName": "作品一",
                    "roleName": "林一",
                    "fields": {
                      "projectName": {"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》女主 林一","sourceType":"explicit"}
                    }
                  }]
                }
                """, "《作品一》女主 林一"));
    }

    @Test
    void workFactsMustAppearInTheirEvidence() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [],
                  "workCandidates": [{
                    "candidateId": "work-1",
                    "projectName": "作品一",
                    "achievementText": "播放量2亿",
                    "fields": {
                      "projectName": {"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》","sourceType":"explicit"},
                      "achievementText": {"candidateValue":"播放量2亿","confidence":0.99,"sourceText":"热度第一","sourceType":"explicit"}
                    }
                  }]
                }
                """, "《作品一》 热度第一 播放量2亿"));
    }

    @Test
    void governedProfileFactsMustBeSupportedByTheirOwnEvidence() {
        assertAll(
                () -> assertUnsupportedProfile("height", "180", "身高：170cm"),
                () -> assertUnsupportedProfile("weight", "50", "体重：45kg"),
                () -> assertUnsupportedProfile("birth_year", "2005", "生日：2004.9"),
                () -> assertUnsupportedProfile("birth_month", "8", "生日：2004.9"),
                () -> assertUnsupportedProfile("birth_day", "18", "生日：2004.9.17"),
                () -> assertUnsupportedProfile("public_name", "\"李小明\"", "演员王火火"),
                () -> assertUnsupportedProfile("origin_place", "\"广东\"", "籍贯：浙江"),
                () -> assertUnsupportedProfile("school_name", "\"北京电影学院\"", "院校：中央戏剧学院"),
                () -> assertUnsupportedProfile("major_name", "\"表演\"", "专业：舞蹈"),
                () -> assertUnsupportedProfile("language_tags", "[\"英语\"]", "语言：粤语"),
                () -> assertUnsupportedProfile("specialty_tags", "[\"武术\"]", "特长：舞蹈"),
                () -> assertUnsupportedProfile("role_type_tags", "[\"御姐\"]", "人物类型：少女"),
                () -> assertUnsupportedProfile(
                        "professional_ability_tags", "[\"威亚\"]", "职业能力：同期声"));
    }

    @Test
    void workIdentityDatesAchievementsAndCollaboratorsMustMatchTheirEvidence() {
        assertAll(
                () -> assertUnsupportedWorkField("projectName", "\"替换作品\"", "《真实作品》女主 林一"),
                () -> assertUnsupportedWorkField("roleName", "\"李四\"", "《作品一》女主 林一"),
                () -> assertUnsupportedWorkField("shootYear", "2025", "《作品一》2024年9月拍摄"),
                () -> assertUnsupportedWorkField("shootMonth", "8", "《作品一》2024年9月拍摄"),
                () -> assertUnsupportedWorkField(
                        "achievementText", "\"播放量2亿\"", "《作品一》平台热度第一"),
                () -> assertUnsupportedWorkField(
                        "collaborators", "[\"李四\"]", "《作品一》合作演员：王五"));
    }

    @Test
    void workEnumsUseControlledChineseOrEnglishSemantics() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [],
                  "workCandidates": [{
                    "candidateId":"work-1","projectName":"作品一","publishStatus":"aired",
                    "fields": {
                      "projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》","sourceType":"explicit"},
                      "publishStatus":{"candidateValue":"aired","confidence":0.99,"sourceText":"状态：待播","sourceType":"explicit"}
                    }
                  }]
                }
                """, "《作品一》 状态：待播"));

        ValidatedExtraction result = validator.validate("""
                {
                  "profileCandidates": [],
                  "workCandidates": [{
                    "candidateId":"work-1","projectName":"作品一","publishStatus":"aired",
                    "roleLevelCode":"female_lead","syncSoundStatus":"sync",
                    "fields": {
                      "projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》","sourceType":"explicit"},
                      "publishStatus":{"candidateValue":"aired","confidence":0.99,"sourceText":"已播","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_lead","confidence":0.99,"sourceText":"女主","sourceType":"explicit"},
                      "syncSoundStatus":{"candidateValue":"sync","confidence":0.99,"sourceText":"同期声","sourceType":"explicit"}
                    }
                  }]
                }
                """, "《作品一》 已播 女主 同期声");

        assertEquals("female_lead", result.workCandidates().get(0).roleLevelCode());
    }

    @Test
    void explicitGenderRequiresDirectSelfDescription() {
        assertAll(
                () -> assertUnsupportedGender("female", "女主"),
                () -> assertUnsupportedGender("female", "演员王火火"),
                () -> assertUnsupportedGender("female", "北京电影学院表演专业"),
                () -> assertUnsupportedGender("male", "男主"),
                () -> assertUnsupportedGender("male", "演员李明"));

        assertEquals("female", validator.validate(genderCandidate("female", "性别：女"), "性别：女")
                .profileCandidate("gender").value());
        assertEquals("male", validator.validate(genderCandidate("male", "男演员"), "职业：男演员")
                .profileCandidate("gender").value());
    }

    @Test
    void mediaPlaceholderCountComesFromTheInputNotTheModel() {
        ValidatedExtraction result = validator.validate("""
                {
                  "profileCandidates": [],
                  "workCandidates": [],
                  "ignoredMediaPlaceholderCount": 99
                }
                """, "模卡[图片][视频]剧照[图片]");

        assertEquals(3, result.ignoredMediaPlaceholderCount());
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"profileCandidates\":[{\"fieldKey\":\"hack\",\"candidateValue\":\"x\"}],\"workCandidates\":[]}",
                "x"));
    }

    private void assertInvalid(String json) {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(json, "测试证据"));
    }

    private void assertUnsupportedProfile(String fieldKey, String candidateValue, String sourceText) {
        String jsonValue = candidateValue.startsWith("\"") || candidateValue.startsWith("[")
                ? candidateValue : "\"" + candidateValue + "\"";
        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "profileCandidates": [{
                    "fieldKey":"%s","candidateValue":%s,"confidence":0.99,
                    "sourceText":"%s","sourceType":"explicit"
                  }],
                  "workCandidates": []
                }
                """.formatted(fieldKey, jsonValue, sourceText), sourceText));
    }

    private void assertUnsupportedWorkField(String fieldKey, String candidateValue, String sourceText) {
        String projectValue = "projectName".equals(fieldKey) ? candidateValue : "\"作品一\"";
        String flatField = "projectName".equals(fieldKey)
                ? "" : ",\n    \"%s\": %s".formatted(fieldKey, candidateValue);
        String extraEvidence = "projectName".equals(fieldKey)
                ? "" : ",\n      \"%s\": {\"candidateValue\":%s,\"confidence\":0.99,"
                        .formatted(fieldKey, candidateValue)
                        + "\"sourceText\":\"" + sourceText + "\",\"sourceType\":\"explicit\"}";
        String projectEvidence = "projectName".equals(fieldKey) ? sourceText : "《作品一》";
        String json = """
                {
                  "profileCandidates": [],
                  "workCandidates": [{
                    "candidateId":"work-1","projectName":%s%s,
                    "fields": {
                      "projectName":{"candidateValue":%s,"confidence":0.99,
                        "sourceText":"%s","sourceType":"explicit"}%s
                    }
                  }]
                }
                """.formatted(projectValue, flatField, projectValue, projectEvidence, extraEvidence);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(json, "《作品一》 " + sourceText));
    }

    private void assertUnsupportedGender(String gender, String sourceText) {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(genderCandidate(gender, sourceText), sourceText));
    }

    private String genderCandidate(String gender, String sourceText) {
        return """
                {
                  "profileCandidates": [{
                    "fieldKey":"gender","candidateValue":"%s","confidence":0.99,
                    "sourceText":"%s","sourceType":"explicit"
                  }],
                  "workCandidates": []
                }
                """.formatted(gender, sourceText);
    }

    private String profileCandidate(String fieldKey, String value) {
        return """
                {
                  "profileCandidates": [{
                    "fieldKey": "%s",
                    "candidateValue": "%s",
                    "confidence": 0.99,
                    "sourceText": "测试证据",
                    "sourceType": "explicit"
                  }],
                  "workCandidates": []
                }
                """.formatted(fieldKey, value);
    }

    private String workCandidate(String fieldKey, Object value) {
        String jsonValue = value instanceof Number ? value.toString() : "\"" + value + "\"";
        return """
                {
                  "profileCandidates": [],
                  "workCandidates": [{
                    "candidateId": "work-1",
                    "projectName": "作品一",
                    "%s": %s
                  }]
                }
                """.formatted(fieldKey, jsonValue);
    }
}
