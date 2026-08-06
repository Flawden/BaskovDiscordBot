package ru.flawden.BascovDiscordBot.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.config.PersistenceProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileGuildPreferencesRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsPlaybackAndDjPreferencesAcrossRepositoryRestart() throws Exception {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();

        assertEquals(new GuildPreferences(100, RepeatMode.OFF), repository.get(42L));

        repository.saveVolume(42L, 73);
        repository.saveRepeatMode(42L, RepeatMode.QUEUE);
        repository.saveAccessMode(42L, PlaybackAccessMode.VOTE_SKIP);
        repository.saveDjRoleId(42L, 987654321L);
        repository.saveVoteSkipPercent(42L, 60);

        assertTrue(Files.isRegularFile(storage));
        FileGuildPreferencesRepository restarted = repository(storage);
        restarted.load();

        assertEquals(new GuildPreferences(
                73,
                RepeatMode.QUEUE,
                PlaybackAccessMode.VOTE_SKIP,
                987654321L,
                60), restarted.get(42L));
    }

    @Test
    void loadsLegacyVolumeAndRepeatFileWithSafeAccessDefaults() throws Exception {
        Path storage = tempDirectory.resolve("legacy.properties");
        Files.writeString(storage, String.join("\n",
                "guild.9.volume=55",
                "guild.9.repeat=TRACK",
                ""));

        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();

        assertEquals(new GuildPreferences(
                55,
                RepeatMode.TRACK,
                PlaybackAccessMode.OPEN,
                0L,
                GuildPreferences.DEFAULT_VOTE_SKIP_PERCENT), repository.get(9L));
    }

    @Test
    void resetRemovesStoredOverrideAndRestoresCurrentDefaults() {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();
        repository.saveVolume(7L, 25);
        repository.saveRepeatMode(7L, RepeatMode.TRACK);
        repository.saveAccessMode(7L, PlaybackAccessMode.DJ_ONLY);
        repository.saveDjRoleId(7L, 777L);
        repository.saveVoteSkipPercent(7L, 75);

        assertEquals(new GuildPreferences(100, RepeatMode.OFF), repository.reset(7L));

        FileGuildPreferencesRepository restarted = repository(storage);
        restarted.load();
        assertEquals(new GuildPreferences(100, RepeatMode.OFF), restarted.get(7L));
    }

    private static FileGuildPreferencesRepository repository(Path storage) {
        PersistenceProperties persistence = new PersistenceProperties();
        persistence.setFile(storage);
        return new FileGuildPreferencesRepository(persistence, new MusicProperties());
    }
}
