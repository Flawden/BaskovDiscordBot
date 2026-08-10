package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedStationTest {

    @Test
    void exposesExactlySixCuratedStations() {
        List<PersonalizedStation> stations = PersonalizedStation.curatedStations();

        assertEquals(6, stations.size());
        assertFalse(stations.contains(PersonalizedStation.CUSTOM));
    }

    @Test
    void mapsStablePublicSlugs() {
        assertEquals(PersonalizedStation.MY_MIX, PersonalizedStation.fromSlug("my-mix"));
        assertEquals(PersonalizedStation.DAILY_MIX, PersonalizedStation.fromSlug("daily-mix"));
        assertEquals(PersonalizedStation.DISCOVERIES, PersonalizedStation.fromSlug("discoveries"));
        assertEquals(PersonalizedStation.DAILY_DISCOVERIES, PersonalizedStation.fromSlug("daily-discoveries"));
        assertEquals(PersonalizedStation.FAMILIAR, PersonalizedStation.fromSlug("familiar"));
        assertEquals(PersonalizedStation.MOOD, PersonalizedStation.fromSlug("mood"));
        assertEquals(PersonalizedStation.MY_MIX, PersonalizedStation.fromSlug("unknown"));
    }

    @Test
    void presetsReuseExistingRadioStrategies() {
        assertEquals(RadioStrategy.SIMILAR, PersonalizedStation.MY_MIX.strategy());
        assertEquals(RadioStrategy.SIMILAR, PersonalizedStation.DAILY_MIX.strategy());
        assertEquals(RadioStrategy.DISCOVERY, PersonalizedStation.DISCOVERIES.strategy());
        assertEquals(RadioStrategy.DISCOVERY, PersonalizedStation.DAILY_DISCOVERIES.strategy());
        assertEquals(RadioStrategy.FAMILIAR, PersonalizedStation.FAMILIAR.strategy());
        assertEquals(RadioStrategy.SIMILAR, PersonalizedStation.MOOD.strategy());
    }

    @Test
    void onlyMoodUsesRecentSessionBiasedSeeds() {
        assertTrue(PersonalizedStation.MOOD.recentSeedsOnly());
        assertFalse(PersonalizedStation.MY_MIX.recentSeedsOnly());
        assertFalse(PersonalizedStation.DAILY_MIX.recentSeedsOnly());
        assertFalse(PersonalizedStation.DISCOVERIES.recentSeedsOnly());
        assertFalse(PersonalizedStation.DAILY_DISCOVERIES.recentSeedsOnly());
        assertFalse(PersonalizedStation.FAMILIAR.recentSeedsOnly());
    }

    @Test
    void dailyStationsAreExplicitAndBoundedToExistingStrategies() {
        assertTrue(PersonalizedStation.DAILY_MIX.dailySeeded());
        assertTrue(PersonalizedStation.DAILY_DISCOVERIES.dailySeeded());
        assertFalse(PersonalizedStation.MY_MIX.dailySeeded());
        assertFalse(PersonalizedStation.DISCOVERIES.dailySeeded());
    }

    @Test
    void dailyDiscoveriesStillUsesHardNoveltyStrategy() {
        assertTrue(PersonalizedStation.DAILY_DISCOVERIES.strategy().hardNovelty());
        assertFalse(PersonalizedStation.DAILY_MIX.strategy().hardNovelty());
    }
}
