package com.kaipai.service.ai;

import com.kaipai.service.ai.profileimport.ProfileImportPromptRuntime;

public interface ProfileImportPromptRuntimeResolver {
    ProfileImportPromptRuntime resolve(String scene);
}
