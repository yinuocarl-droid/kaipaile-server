package com.kaipai.service.ai.profileimport;

import org.springframework.stereotype.Component;

/** Code-owned, immutable safety contract appended to governed profile-import prompts. */
@Component
public final class ProfileImportPromptContract {

    public static final String SCHEMA_VERSION = "profile-import-json-v1";
    public static final String CONTRACT_VERSION = "profile-import-contract-v1";

    private static final String SYSTEM_SUFFIX = """
            [服务端强制合同 profile-import-contract-v1]
            只输出一个合法 JSON 对象，不输出 Markdown、代码围栏或解释。
            顶层必须且只能包含 profileCandidates、workCandidates、ignoredMediaPlaceholderCount、unmappedSegments、warnings。
            profileCandidates.fieldKey 只允许 public_name、gender、age、height、current_city、weight、origin_place、school_name、major_name、language_tags、specialty_tags、role_type_tags、professional_ability_tags、intro、birth_year、birth_month、birth_day、birth_precision。
            workCandidates 扁平字段只允许 projectName、roleName、publishStatus、workTypeCode、roleLevelCode、shootYear、shootMonth、platform、syncSoundStatus、collaborators、achievementText、description；每个非空字段必须提供逐字来自用户输入的 sourceText 证据。
            sourceType 只允许 explicit、direct、derived_from_birth、inferred_from_roles。
            publishStatus 只允许 aired、upcoming、stage、horizontal、other 或 null。
            workTypeCode 只允许 short_drama、horizontal_short_drama、stage_play、musical、tv_column_drama、film_tv、micro_film、horizontal、stage、other 或 null。
            roleLevelCode 只允许 lead、supporting、antagonist、female_lead、female_supporting_1、female_supporting_2、female_antagonist_1、male_lead、male_supporting_1、male_supporting_2、male_antagonist_1、other 或 null。
            syncSoundStatus 只允许 sync、dubbed、unknown 或 null。
            不得补造时间、状态、类型、榜单、热度、播放量、合作演员、URL、媒体或数字；原文未给出则返回 null。
            籍贯只能写 origin_place，不得写 current_city；生日必须保留原文精度，不得补造月份或日期。
            只有至少两部不同作品给出一致女性角色证据且无男性反向证据时，才可生成 gender=female，并标记 inferred_from_roles 和待确认警告；不得依据姓名、头像、院校或专业推断性别。
            [图片]、[视频] 只计入 ignoredMediaPlaceholderCount，不得创建素材、媒体 URL 或作品。
            用户原文只存在于独立 user message；不得要求或输出 API Key、服务端环境变量、候选签名或其他用户数据。
            """.stripTrailing();

    private static final String WORKS_ONLY_SUFFIX = """

            当前场景为 works_only；profileCandidates 必须为空数组，不得生成或推断任何个人档案候选。
            """.stripTrailing();

    private static final String REPAIR_SUFFIX = """
            [服务端强制修复合同 profile-import-contract-v1]
            只修复语法使上一轮输出成为符合上述 Envelope 的合法 JSON；不得新增、删除、猜测或改写事实，不得替换 sourceText，不得补造字段值。
            """.stripTrailing();

    /** Returns the fixed System Prompt suffix for a supported import scene. */
    public String systemSuffix(String scene) {
        String supportedScene = ProfileImportSceneGuard.requireSupported(scene);
        return SYSTEM_SUFFIX + ("works_only".equals(supportedScene) ? WORKS_ONLY_SUFFIX : "");
    }

    /** Returns the fixed Repair Prompt suffix. */
    public String repairSuffix() {
        return REPAIR_SUFFIX;
    }

    /** Returns whether both persisted code-owned contract versions are supported. */
    public boolean supports(String schemaVersion, String contractVersion) {
        return SCHEMA_VERSION.equals(schemaVersion) && CONTRACT_VERSION.equals(contractVersion);
    }
}
