package com.kaipai.service.ai.profilecard;

import java.util.Locale;

public final class AiProfileCardThemeResolver {

    private AiProfileCardThemeResolver() {
    }

    public static Theme resolve(String templateSceneCode, String styleCode) {
        String normalized = ((styleCode == null ? "" : styleCode) + " " + (templateSceneCode == null ? "" : templateSceneCode))
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("urban")) {
            return new Theme(
                    "#0f1115",
                    "#181d24",
                    "#252d37",
                    "#b6463d",
                    "#fff3df",
                    "rgba(255, 245, 230, 0.72)",
                    "rgba(255, 239, 214, 0.18)");
        }
        if (normalized.contains("commercial")) {
            return new Theme(
                    "#eef1f4",
                    "#fbfcfd",
                    "#dde5ec",
                    "#243a53",
                    "#171b21",
                    "rgba(35, 43, 53, 0.64)",
                    "rgba(44, 52, 64, 0.16)");
        }
        if (normalized.contains("artistic")) {
            return new Theme(
                    "#171713",
                    "#20211f",
                    "#2e2e28",
                    "#8b553b",
                    "#f5ead4",
                    "rgba(245, 234, 212, 0.72)",
                    "rgba(236, 229, 210, 0.18)");
        }
        if (normalized.contains("costume")) {
            return new Theme(
                    "#efe0c4",
                    "#fff5e2",
                    "#ead8b7",
                    "#9a3e34",
                    "#2b1e16",
                    "rgba(43, 30, 22, 0.66)",
                    "rgba(143, 92, 42, 0.20)");
        }
        return new Theme(
                "#eee3cf",
                "#fff7eb",
                "#eadfce",
                "#8c6f4f",
                "#231b15",
                "rgba(35, 27, 21, 0.64)",
                "rgba(35, 27, 21, 0.12)");
    }

    public record Theme(
            String backgroundColor,
            String surfaceColor,
            String surfaceStrongColor,
            String accentColor,
            String textColor,
            String mutedTextColor,
            String borderColor
    ) {
    }
}
