package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedStationTest {

    @Test
    void exposesExactlySevenCuratedStations() {
        List<PersonalizedStation> stations = PersonalizedStation.curatedStations();

        assertEquals(7, stations.size());
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
        assertEquals(PersonalizedStation.THEME, PersonalizedStation.fromSlug("theme"));
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
        assertEquals(RadioStrategy.SIMILAR, PersonalizedStation.THEME.strategy());
    }

    @Test
    void onlyMoodUsesRecentSessionBiasedSeeds() {
        assertTrue(PersonalizedStation.MOOD.recentSeedsOnly());
        assertFalse(PersonalizedStation.MY_MIX.recentSeedsOnly());
        assertFalse(PersonalizedStation.DAILY_MIX.recentSeedsOnly());
        assertFalse(PersonalizedStation.DISCOVERIES.recentSeedsOnly());
        assertFalse(PersonalizedStation.DAILY_DISCOVERIES.recentSeedsOnly());
        assertFalse(PersonalizedStation.FAMILIAR.recentSeedsOnly());
        assertFalse(PersonalizedStation.THEME.recentSeedsOnly());
    }

    @Test
    void dailyStationsAreExplicitAndBoundedToExistingStrategies() {
        assertTrue(PersonalizedStation.DAILY_MIX.dailySeeded());
        assertTrue(PersonalizedStation.DAILY_DISCOVERIES.dailySeeded());
        assertFalse(PersonalizedStation.MY_MIX.dailySeeded());
        assertFalse(PersonalizedStation.DISCOVERIES.dailySeeded());
        assertFalse(PersonalizedStation.THEME.dailySeeded());
    }

    @Test
    void dailyDiscoveriesStillUsesHardNoveltyStrategy() {
        assertTrue(PersonalizedStation.DAILY_DISCOVERIES.strategy().hardNovelty());
        assertFalse(PersonalizedStation.DAILY_MIX.strategy().hardNovelty());
    }

    @Test
    void diversityAndThemePoliciesAreExplicit() {
        assertTrue(PersonalizedStation.MY_MIX.diversityControlled());
        assertTrue(PersonalizedStation.DAILY_MIX.diversityControlled());
        assertTrue(PersonalizedStation.DISCOVERIES.diversityControlled());
        assertTrue(PersonalizedStation.DAILY_DISCOVERIES.diversityControlled());
        assertTrue(PersonalizedStation.MOOD.diversityControlled());
        assertTrue(PersonalizedStation.THEME.diversityControlled());
        assertFalse(PersonalizedStation.FAMILIAR.diversityControlled());
        assertTrue(PersonalizedStation.THEME.themeRequired());
        assertFalse(PersonalizedStation.MY_MIX.themeRequired());
    }
}
