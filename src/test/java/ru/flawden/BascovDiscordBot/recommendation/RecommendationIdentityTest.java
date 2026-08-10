package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationIdentityTest {

    @Test
    void identityIgnoresCasePunctuationAndSpacing() {
        assertEquals(
                RecommendationIdentity.of("Linkin Park", "Numb"),
                RecommendationIdentity.of("  LINKIN   PARK ", "Numb!!!"));
    }
    @Test
    void legacyFacadeMatchesCanonicalTrackIdentityStableKey() {
        assertEquals(
                TrackIdentity.of("Linkin Park", "Numb").stableKey(),
                RecommendationIdentity.of("Linkin Park", "Numb"));
    }

}
