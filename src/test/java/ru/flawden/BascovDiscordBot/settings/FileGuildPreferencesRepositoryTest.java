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
    void persistsAdministrationRestrictionsAndAuditAcrossRepositoryRestart() throws Exception {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();

        assertEquals(new GuildPreferences(100, RepeatMode.OFF), repository.get(42L));

        repository.saveVolume(42L, 73);
        repository.saveRepeatMode(42L, RepeatMode.QUEUE);
        repository.saveAccessMode(42L, PlaybackAccessMode.VOTE_SKIP);
        repository.saveRequestAccessMode(42L, RequestAccessMode.DJ_ONLY);
        repository.saveDjRoleId(42L, 987654321L);
        repository.saveManagerRoleId(42L, 1122334455L);
        repository.saveModeratorRoleId(42L, 2233445566L);
        repository.saveRequesterQueueLimit(42L, 7);
        repository.saveMusicChannelId(42L, 5566778899L);
        repository.saveVoteSkipPercent(42L, 60);
        repository.recordAudit(42L, 777L, "manager-role=1122334455");
        repository.recordAudit(42L, 888L, "music-channel=5566778899");

        assertTrue(Files.isRegularFile(storage));
        FileGuildPreferencesRepository restarted = repository(storage);
        restarted.load();

        assertEquals(new GuildPreferences(
                73,
                RepeatMode.QUEUE,
                PlaybackAccessMode.VOTE_SKIP,
                RequestAccessMode.DJ_ONLY,
                987654321L,
                1122334455L,
                2233445566L,
                5566778899L,
                60,
                7), restarted.get(42L));
        assertEquals(2, restarted.recentAudit(42L).size());
        assertEquals("music-channel=5566778899", restarted.recentAudit(42L).get(0).action());
        assertEquals(888L, restarted.recentAudit(42L).get(0).actorUserId());
    }

    @Test
    void loadsLegacyVolumeAndRepeatFileWithSafeAdministrationDefaults() throws Exception {
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
                RequestAccessMode.OPEN,
                0L,
                0L,
                0L,
                GuildPreferences.DEFAULT_VOTE_SKIP_PERCENT), repository.get(9L));
    }

    @Test
    void replaceIsAtomicSurfaceForImportedProfiles() {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();

        GuildPreferences imported = new GuildPreferences(
                44,
                RepeatMode.TRACK,
                PlaybackAccessMode.DJ_ONLY,
                RequestAccessMode.DJ_ONLY,
                111L,
                222L,
                223L,
                333L,
                75,
                5);

        assertEquals(imported, repository.replace(7L, imported));
        FileGuildPreferencesRepository restarted = repository(storage);
        restarted.load();
        assertEquals(imported, restarted.get(7L));
    }

    @Test
    void resetRemovesStoredOverrideAndRestoresCurrentDefaults() {
        Path storage = tempDirectory.resolve("guild-settings.properties");
        FileGuildPreferencesRepository repository = repository(storage);
        repository.load();
        repository.saveVolume(7L, 25);
        repository.saveRepeatMode(7L, RepeatMode.TRACK);
        repository.saveAccessMode(7L, PlaybackAccessMode.DJ_ONLY);
        repository.saveRequestAccessMode(7L, RequestAccessMode.DJ_ONLY);
        repository.saveDjRoleId(7L, 777L);
        repository.saveManagerRoleId(7L, 778L);
        repository.saveModeratorRoleId(7L, 780L);
        repository.saveRequesterQueueLimit(7L, 4);
        repository.saveMusicChannelId(7L, 779L);
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
