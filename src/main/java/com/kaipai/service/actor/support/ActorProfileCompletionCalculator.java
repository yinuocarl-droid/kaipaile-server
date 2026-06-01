package com.kaipai.service.actor.support;

import com.kaipai.model.actor.entity.ActorProfile;

public final class ActorProfileCompletionCalculator {

    private ActorProfileCompletionCalculator() {
    }

    public static int calculate(ActorProfile profile) {
        if (profile == null) {
            return 0;
        }

        int score = 0;
        if (hasText(profile.getAvatarUrl())) {
            score += 10;
        }
        if (hasText(profile.getNickName()) && profile.getGender() != null && profile.getAge() != null
                && profile.getHeight() != null && hasText(profile.getLocationCity())) {
            score += 15;
        }
        if (hasText(profile.getPhotoUrls())) {
            score += 15;
        }
        if (hasText(profile.getVideoUrl())) {
            score += 15;
        }
        if (hasText(profile.getIntro()) && profile.getIntro().trim().length() >= 20) {
            score += 10;
        }
        if (hasText(profile.getSkillTag())) {
            score += 5;
        }
        if (hasText(profile.getExperienceDesc())) {
            score += 15;
        }
        if (hasText(profile.getStyleTag())) {
            score += 10;
        }
        return score;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
