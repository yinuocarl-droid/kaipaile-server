package com.kaipai.controller.api.actor;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileRespDTO;
import com.kaipai.service.actor.ActorProfileService;
import com.kaipai.service.actor.ActorProfileWriteService;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActorProfileControllerContractTest {

    private static final long USER_ID = 7L;

    private ActorProfileService legacyProfileService;
    private ActorProfileWriteService profileWriteService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        legacyProfileService = org.mockito.Mockito.mock(ActorProfileService.class);
        profileWriteService = org.mockito.Mockito.mock(ActorProfileWriteService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ActorProfileController(legacyProfileService, profileWriteService))
                .build();
    }

    @Test
    void getMineReturnsVersionedProfileFromWriteService() throws Exception {
        ActorProfileRespDTO profile = versionedProfile(3, 12L);
        when(profileWriteService.mine(USER_ID)).thenReturn(profile);

        mockMvc.perform(get("/actor/profile/mine").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicName").value("王火火"))
                .andExpect(jsonPath("$.data.profileVersion").value(3))
                .andExpect(jsonPath("$.data.workLibraryVersion").value(12));

        verify(profileWriteService).mine(USER_ID);
        verifyNoInteractions(legacyProfileService);
    }

    @Test
    void getMineReturnsVersionZeroEmptyDraftWithHttp200() throws Exception {
        ActorProfileRespDTO emptyDraft = new ActorProfileRespDTO();
        emptyDraft.setUserId(USER_ID);
        emptyDraft.setProfileVersion(0);
        emptyDraft.setWorkLibraryVersion(0L);
        when(profileWriteService.mine(USER_ID)).thenReturn(emptyDraft);

        mockMvc.perform(get("/actor/profile/mine").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(0))
                .andExpect(jsonPath("$.data.workLibraryVersion").value(0))
                .andExpect(jsonPath("$.data.actorProfileId").value(nullValue()))
                .andExpect(jsonPath("$.data.publicName").value(nullValue()))
                .andExpect(jsonPath("$.data.gender").value(nullValue()))
                .andExpect(jsonPath("$.data.languageTags").isEmpty())
                .andExpect(jsonPath("$.data.specialtyTags").isEmpty())
                .andExpect(jsonPath("$.data.roleTypeTags").isEmpty())
                .andExpect(jsonPath("$.data.professionalAbilityTags").isEmpty());

        verify(profileWriteService).mine(USER_ID);
        verifyNoInteractions(legacyProfileService);
    }

    @Test
    void getCareerAliasKeepsReturningVersionedProfile() throws Exception {
        when(profileWriteService.mine(USER_ID)).thenReturn(versionedProfile(3, 12L));

        mockMvc.perform(get("/actor/profile/mine/career").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(3))
                .andExpect(jsonPath("$.data.workLibraryVersion").value(12));

        verify(profileWriteService).mine(USER_ID);
        verifyNoInteractions(legacyProfileService);
    }

    @Test
    void getCareerMineIsDeprecatedCompatibilityAlias() throws Exception {
        var method = ActorProfileController.class.getDeclaredMethod("careerMine", Authentication.class);

        assertTrue(method.isAnnotationPresent(Deprecated.class));
        Operation operation = method.getAnnotation(Operation.class);
        assertNotNull(operation);
        assertTrue(operation.deprecated());
        assertTrue(operation.description().contains("GET /api/actor/profile/mine"));
        assertFalse(operation.description().contains("/mine/career"));
    }

    @Test
    void getLegacyMineReturnsAggregateProfileFromLegacyService() throws Exception {
        ActorProfileDTO profile = legacyProfile();
        when(legacyProfileService.mine(USER_ID)).thenReturn(profile);

        mockMvc.perform(get("/actor/profile/mine/legacy").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.name").value("王火火"));

        verify(legacyProfileService).mine(USER_ID);
        verify(profileWriteService, never()).mine(any());
    }

    @Test
    void getLegacyMineIsDeprecatedAndPointsCallersToVersionedRoute() throws Exception {
        var method = ActorProfileController.class.getDeclaredMethod("legacyMine", Authentication.class);

        assertTrue(method.isAnnotationPresent(Deprecated.class));
        Operation operation = method.getAnnotation(Operation.class);
        assertNotNull(operation);
        assertTrue(operation.deprecated());
        assertTrue(operation.description().contains("GET /api/actor/profile/mine"));
        assertFalse(operation.description().contains("/mine/career"));
    }

    @Test
    void putMineKeepsVersionedSaveContract() throws Exception {
        when(profileWriteService.saveMine(any(), any())).thenReturn(versionedProfile(4, 12L));

        mockMvc.perform(put("/actor/profile/mine")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedProfileVersion": 3,
                                  "avatarAssetId": 81,
                                  "core": {
                                    "publicName": "王火火",
                                    "gender": "female",
                                    "age": 21,
                                    "height": 170,
                                    "currentCity": "杭州"
                                  },
                                  "career": {},
                                  "intro": "演员"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(4))
                .andExpect(jsonPath("$.data.workLibraryVersion").value(12));

        verify(profileWriteService).saveMine(any(Long.class), any(ActorProfileMineUpdateDTO.class));
        verifyNoInteractions(legacyProfileService);
    }

    private TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken(USER_ID, null);
    }

    private ActorProfileRespDTO versionedProfile(int profileVersion, long workLibraryVersion) {
        ActorProfileRespDTO profile = new ActorProfileRespDTO();
        profile.setUserId(USER_ID);
        profile.setPublicName("王火火");
        profile.setProfileVersion(profileVersion);
        profile.setWorkLibraryVersion(workLibraryVersion);
        return profile;
    }

    private ActorProfileDTO legacyProfile() {
        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setUserId(USER_ID);
        profile.setName("王火火");
        return profile;
    }
}
