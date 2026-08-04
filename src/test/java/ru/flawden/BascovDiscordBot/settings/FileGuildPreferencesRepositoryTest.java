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
    void persistsVolumeAndRepeatAcrossRepositoryRestart() throws Exception {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();

        assertEquals(new GuildPreferences(100, RepeatMode.OFF), repository.get(42L));

        repository.saveVolume(42L, 73);
        repository.saveRepeatMode(42L, RepeatMode.QUEUE);

        assertTrue(Files.isRegularFile(storage));
        FileGuildPreferencesRepository restarted = repository(storage);
        restarted.load();

        assertEquals(new GuildPreferences(73, RepeatMode.QUEUE), restarted.get(42L));
    }

    @Test
    void resetRemovesStoredOverrideAndRestoresCurrentDefaults() {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();
        repository.saveVolume(7L, 25);
        repository.saveRepeatMode(7L, RepeatMode.TRACK);

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
