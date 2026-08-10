package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedStationTest {

    @Test
    void exposesExactlyFourCuratedStations() {
        List<PersonalizedStation> stations = PersonalizedStation.curatedStations();

        assertEquals(4, stations.size());
        assertFalse(stations.contains(PersonalizedStation.CUSTOM));
    }

    @Test
    void mapsStablePublicSlugs() {
        assertEquals(PersonalizedStation.MY_MIX, PersonalizedStation.fromSlug("my-mix"));
        assertEquals(PersonalizedStation.DISCOVERIES, PersonalizedStation.fromSlug("discoveries"));
        assertEquals(PersonalizedStation.FAMILIAR, PersonalizedStation.fromSlug("familiar"));
        assertEquals(PersonalizedStation.MOOD, PersonalizedStation.fromSlug("mood"));
        assertEquals(PersonalizedStation.MY_MIX, PersonalizedStation.fromSlug("unknown"));
    }

    @Test
    void presetsReuseExistingRadioStrategies() {
        assertEquals(RadioStrategy.SIMILAR, PersonalizedStation.MY_MIX.strategy());
        assertEquals(RadioStrategy.DISCOVERY, PersonalizedStation.DISCOVERIES.strategy());
        assertEquals(RadioStrategy.FAMILIAR, PersonalizedStation.FAMILIAR.strategy());
        assertEquals(RadioStrategy.SIMILAR, PersonalizedStation.MOOD.strategy());
    }

    @Test
    void onlyMoodUsesRecentSessionBiasedSeeds() {
        assertTrue(PersonalizedStation.MOOD.recentSeedsOnly());
        assertFalse(PersonalizedStation.MY_MIX.recentSeedsOnly());
        assertFalse(PersonalizedStation.DISCOVERIES.recentSeedsOnly());
        assertFalse(PersonalizedStation.FAMILIAR.recentSeedsOnly());
    }
}
