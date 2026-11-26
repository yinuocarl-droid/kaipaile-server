package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileImportPromptPolicyTest {

    private ProfileImportPromptPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProfileImportPromptPolicy(new ProfileImportPromptContract());
    }

    @Test
    void exactEditableBodyBoundsAreAccepted() {
        assertDoesNotThrow(() -> policy.validateBodies("S".repeat(200), "R".repeat(20)));
        assertDoesNotThrow(() -> policy.validateBodies("S".repeat(16000), "R".repeat(1000)));
        assertDoesNotThrow(() -> policy.validateBodies(
                "S".repeat(200) + "\t\r\n", "R".repeat(20) + "\t\r\n"));
    }

    @Test
    void crlfAndLfEquivalentBodiesReceiveTheSameNormalizedLengthDecision() {
        assertPromptInvalid(
                () -> policy.validateBodies("S".repeat(198) + "\r\n", validRepair()),
                null);
        assertPromptInvalid(
                () -> policy.validateBodies("S".repeat(198) + "\n", validRepair()),
                null);
        assertDoesNotThrow(() -> policy.validateBodies(
                "S".repeat(199) + "\r\n", validRepair()));
        assertDoesNotThrow(() -> policy.validateBodies(
                "S".repeat(199) + "\n", validRepair()));
        assertDoesNotThrow(() -> policy.validateBodies(
                "S".repeat(15999) + "\r\n", validRepair()));
        assertDoesNotThrow(() -> policy.validateBodies(
                "S".repeat(15999) + "\n", validRepair()));

        assertPromptInvalid(
                () -> policy.validateBodies(validSystem(), "R".repeat(18) + "\r\n"),
                null);
        assertPromptInvalid(
                () -> policy.validateBodies(validSystem(), "R".repeat(18) + "\n"),
                null);
        assertDoesNotThrow(() -> policy.validateBodies(
                validSystem(), "R".repeat(19) + "\r\n"));
        assertDoesNotThrow(() -> policy.validateBodies(
                validSystem(), "R".repeat(19) + "\n"));
        assertDoesNotThrow(() -> policy.validateBodies(
                validSystem(), "R".repeat(999) + "\r\n"));
        assertDoesNotThrow(() -> policy.validateBodies(
                validSystem(), "R".repeat(999) + "\n"));
    }

    @Test
    void astralCharactersCountAsOneUnicodeCodePointAtEditableBodyBounds() {
        String emoji = "\uD83D\uDE00";

        assertPromptInvalid(
                () -> policy.validateBodies(emoji.repeat(100), validRepair()),
                null);
        assertDoesNotThrow(() -> policy.validateBodies(
                emoji.repeat(200), validRepair()));
        assertPromptInvalid(
                () -> policy.validateBodies(validSystem(), emoji.repeat(10)),
                null);
        assertDoesNotThrow(() -> policy.validateBodies(
                validSystem(), emoji.repeat(20)));
    }

    @ParameterizedTest
    @MethodSource("outOfBoundsBodies")
    void editableBodyBoundsAreRejected(String systemBody, String repairBody) {
        assertPromptInvalid(() -> policy.validateBodies(systemBody, repairBody), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "${SECRET}", "#{systemProperties}", "{{user.name}}", "}}", "<%= env.API_KEY %>", "%>"
    })
    void everyVariableSyntaxTokenIsRejectedWithoutEcho(String expression) {
        assertPromptInvalid(
                () -> policy.validateBodies(validSystem() + expression, validRepair()),
                expression);
        assertPromptInvalid(
                () -> policy.validateBodies(validSystem(), validRepair() + expression),
                expression);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8, 11, 12, 31, 127, 128, 159})
    void nulAndIsoControlsOtherThanLfCrTabAreRejectedWithoutEcho(int codePoint) {
        String rejected = Character.toString(codePoint);
        assertPromptInvalid(
                () -> policy.validateBodies(validSystem() + rejected, validRepair()),
                rejected);
    }

    @Test
    void onlySupportedSceneAndContractVersionsAreAccepted() {
        assertDoesNotThrow(() -> policy.validateTemplateAndVersion(
                template("full_profile"), version(11L)));
        assertDoesNotThrow(() -> policy.validateTemplateAndVersion(
                template("works_only"), version(11L)));

        AiProfileImportPromptTemplate unsupportedScene = template("profile_and_secrets");
        assertPromptInvalid(
                () -> policy.validateTemplateAndVersion(unsupportedScene, version(11L)),
                unsupportedScene.getScene());

        AiProfileImportPromptVersion unsupportedSchema = version(11L);
        unsupportedSchema.setSchemaVersion("profile-import-json-v2");
        assertPromptInvalid(
                () -> policy.validateTemplateAndVersion(
                        template("full_profile"), unsupportedSchema),
                unsupportedSchema.getSchemaVersion());

        AiProfileImportPromptVersion unsupportedContract = version(11L);
        unsupportedContract.setContractVersion("profile-import-contract-v2");
        assertPromptInvalid(
                () -> policy.validateTemplateAndVersion(
                        template("full_profile"), unsupportedContract),
                unsupportedContract.getContractVersion());
    }

    @Test
    void templateAndVersionOwnershipMismatchIsRejected() {
        assertPromptInvalid(
                () -> policy.validateTemplateAndVersion(
                        template("full_profile"), version(12L)),
                null);
    }

    @Test
    void renderedSystemPromptCannotExceedTwentyThousandCharacters() {
        assertDoesNotThrow(() -> policy.validateRenderedSystem("S".repeat(20000)));
        assertDoesNotThrow(() -> policy.validateRenderedSystem(
                "S".repeat(19999) + "\r\n"));
        assertDoesNotThrow(() -> policy.validateRenderedSystem(
                "\uD83D\uDE00".repeat(20000)));
        assertPromptInvalid(
                () -> policy.validateRenderedSystem("S".repeat(20001)),
                "S".repeat(20001));
        assertPromptInvalid(
                () -> policy.validateRenderedSystem("\uD83D\uDE00".repeat(20001)),
                null);
    }

    @Test
    void nullInputsUseOnlyTheStablePromptInvalidError() {
        assertPromptInvalid(() -> policy.validateBodies(null, validRepair()), null);
        assertPromptInvalid(() -> policy.validateBodies(validSystem(), null), null);
        assertPromptInvalid(() -> policy.validateTemplateAndVersion(null, version(11L)), null);
        assertPromptInvalid(() -> policy.validateTemplateAndVersion(
                template("full_profile"), null), null);
        assertPromptInvalid(() -> policy.validateRenderedSystem(null), null);
    }

    private void assertPromptInvalid(Runnable action, String rejectedValue) {
        BizException error = assertThrows(BizException.class, action::run);
        assertEquals(46019, error.getCode());
        assertEquals("Prompt 模板或操作参数无效", error.getMessage());
        if (rejectedValue != null && !rejectedValue.isEmpty()) {
            assertFalse(error.getMessage().contains(rejectedValue));
        }
    }

    private AiProfileImportPromptTemplate template(String scene) {
        AiProfileImportPromptTemplate template = new AiProfileImportPromptTemplate();
        template.setTemplateId(11L);
        template.setTemplateCode(scene);
        template.setScene(scene);
        return template;
    }

    private AiProfileImportPromptVersion version(Long templateId) {
        AiProfileImportPromptVersion version = new AiProfileImportPromptVersion();
        version.setTemplateId(templateId);
        version.setSchemaVersion(ProfileImportPromptContract.SCHEMA_VERSION);
        version.setContractVersion(ProfileImportPromptContract.CONTRACT_VERSION);
        return version;
    }

    private static String validSystem() {
        return "系统正文".repeat(50);
    }

    private static String validRepair() {
        return "修复正文".repeat(5);
    }

    private static Stream<Arguments> outOfBoundsBodies() {
        return Stream.of(
                Arguments.of("S".repeat(199), "R".repeat(20)),
                Arguments.of("S".repeat(16001), "R".repeat(20)),
                Arguments.of("S".repeat(200), "R".repeat(19)),
                Arguments.of("S".repeat(200), "R".repeat(1001)));
    }
}
