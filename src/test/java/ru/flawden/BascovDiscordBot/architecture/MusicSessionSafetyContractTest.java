package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSessionSafetyContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void playerManagerIsSpringManagedAndHasNoGlobalSingletonAccess() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");
        String allSources = Files.walk(MAIN)
                .filter(path -> path.toString().endsWith(".java"))
                .map(MusicSessionSafetyContractTest::readUnchecked)
                .reduce("", String::concat);

        assertTrue(player.contains("@Component"));
        assertTrue(player.contains("@PreDestroy"));
        assertFalse(allSources.contains("getINSTANCE()"));
    }

    @Test
    void queueIsBoundedAndIdleSessionsAreReleased() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(scheduler.contains("LinkedBlockingDeque<TrackRequest> queue"));
        assertTrue(scheduler.contains(
                "new LinkedBlockingDeque<>(maxQueueSize + maxHistorySize)"));
        assertTrue(scheduler.contains("queue.size() >= maxQueueSize"));
        assertTrue(scheduler.contains(
                "maxHistorySize = Math.max(1, Math.min(maxQueueSize, 25))"));
        assertTrue(scheduler.contains("TRACK_TOO_LONG"));
        assertTrue(scheduler.contains("STREAM_NOT_ALLOWED"));
        assertTrue(player.contains("scheduleIdleDisconnect"));
        assertTrue(player.contains("stopAndRelease"));
    }

    @Test
    void mutatingMusicCommandsUseSharedVoicePolicy() throws IOException {
        for (String command : new String[]{
                "PauseEvent.java",
                "PlayEvent.java",
                "SearchEvent.java",
                "SetPlayingTimeEvent.java",
                "SkipEvent.java",
                "StopEvent.java"}) {
            String source = read("commands/music/" + command);
            assertTrue(source.contains("MusicControlPolicy"), command);
            assertTrue(source.contains("MusicCommandReply.allowOrReply"), command);
        }
    }

    @Test
    void readOnlyCommandsDoNotCreateEmptyMusicSessions() throws IOException {
        String song = read("commands/music/SongNameEvent.java");
        String queue = read("commands/music/TrackListEvent.java");

        assertTrue(song.contains("findMusicManager"));
        assertTrue(queue.contains("findMusicManager"));
        assertFalse(song.contains("getMusicManager"));
        assertFalse(queue.contains("getMusicManager"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
