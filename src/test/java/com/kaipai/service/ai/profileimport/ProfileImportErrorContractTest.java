package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kaipai.common.exception.GlobalExceptionHandler;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfileImportErrorContractTest {

    @Test
    void importErrorMapAndEnvelopeAreStable() {
        assertEquals(46001, ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.code());
        assertEquals(
                "PROFILE_IMPORT_DISABLED",
                ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.errorCode());
        R<Void> response = new GlobalExceptionHandler().handleBizException(
                ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.toException());
        assertEquals("PROFILE_IMPORT_DISABLED", response.getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({
        "PROFILE_IMPORT_PROMPT_VERSION_CONFLICT,46018",
        "PROFILE_IMPORT_PROMPT_INVALID,46019",
        "PROFILE_IMPORT_PROMPT_TEST_REQUIRED,46020",
        "PROFILE_IMPORT_PROMPT_TEST_STALE,46021",
        "PROFILE_IMPORT_PROMPT_STATE_CONFLICT,46022"
    })
    void promptGovernanceErrorsHaveStableNumericAndStringEnvelope(String name, int code) {
        ProfileDomainErrorCode value = ProfileDomainErrorCode.valueOf(name);

        assertEquals(code, value.code());
        R<Void> response = new GlobalExceptionHandler().handleBizException(value.toException());
        assertEquals(name, response.getErrorCode());
        assertEquals(code, response.getCode());
    }
}
