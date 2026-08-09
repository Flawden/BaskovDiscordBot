package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartRadioAutoplayContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void radioRefillIsBoundedAndYieldsToHumanActivity() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(player.contains("manager.getActivityVersion() != activityVersion"));
        assertTrue(player.contains("failures >= 3"));
        assertTrue(player.contains("new TrackRequester(0L, \"📻 Radio\")"));
        assertTrue(player.contains("idleScheduler.schedule(() -> triggerRadioRefill"));
    }

    @Test
    void radioUsesLocalSeedsAndAvoidsRecentTracks() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");
        String insights = read("library/PersonalListeningInsights.java");

        assertTrue(player.contains("musicLibraryRepository.favorites"));
        assertTrue(player.contains("musicLibraryRepository.personalHistory"));
        assertTrue(player.contains("musicLibraryRepository.history"));
        assertTrue(player.contains("recentTrackKeys"));
        assertTrue(insights.contains("discoverySeeds"));
    }

    @Test
    void radioStateIsEphemeralAndNotAddedToPersistentFormats() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");
        String sessions = read("session/FileMusicSessionRepository.java");
        String settings = read("settings/FileGuildPreferencesRepository.java");

        assertTrue(player.contains("private final Map<Long, RadioState> radioStates"));
        assertTrue(player.contains("radioStates.clear()"));
        assertFalse(sessions.contains("RadioState"));
        assertFalse(settings.contains("radio-mode"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
