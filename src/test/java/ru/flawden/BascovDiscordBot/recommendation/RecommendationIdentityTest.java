package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationIdentityTest {

    @Test
    void identityIgnoresCasePunctuationAndSpacing() {
        assertEquals(
                RecommendationIdentity.of("Linkin Park", "Numb"),
                RecommendationIdentity.of("  LINKIN   PARK ", "Numb!!!"));
    }
}
