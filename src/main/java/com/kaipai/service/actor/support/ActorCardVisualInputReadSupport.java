package com.kaipai.service.actor.support;

import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import java.sql.SQLException;

/** Precise rolling-deploy compatibility for legacy cards before V20261022_003 exists. */
public final class ActorCardVisualInputReadSupport {

    private static final String TABLE_NAME = "actor_card_visual_input";

    private ActorCardVisualInputReadSupport() {
    }

    public static ActorCardVisualInput selectOrLegacy(
            ActorCardVisualInputMapper mapper,
            Long cardId) {
        try {
            return mapper.selectByCardId(cardId);
        } catch (RuntimeException error) {
            if (isMissingVisualInputTable(error)) {
                return null;
            }
            throw error;
        }
    }

    public static boolean isMissingVisualInputTable(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof SQLException sql
                    && sql.getErrorCode() == 1146
                    && String.valueOf(sql.getMessage()).contains(TABLE_NAME)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
