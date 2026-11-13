package com.itriguero.homeclock;

import java.util.Locale;

/**
 * Traduce el estado bruto de 365Scores a los estados internos de Match.
 */
final class MatchStatusMapper {

    private MatchStatusMapper() {
    }

    static MatchStatus from365(int group, String statusText) {
        String text = statusText == null ? "" : statusText.toLowerCase(Locale.ROOT);

        if (group == 4) {
            if (text.contains("aplazado") || text.contains("postponed")
                    || text.contains("suspend")) {
                return MatchStatus.POSTPONED;
            }
            if (text.contains("cancelado") || text.contains("cancelled")
                    || text.contains("canceled")) {
                return MatchStatus.CANCELLED;
            }
            return MatchStatus.FINISHED;
        }
        if (group == 3) return MatchStatus.LIVE;
        if (group == 2) return MatchStatus.SCHEDULED;
        return MatchStatus.UNKNOWN;
    }
}
