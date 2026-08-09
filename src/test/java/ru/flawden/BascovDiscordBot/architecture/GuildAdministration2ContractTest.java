package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildAdministration2ContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void settingsExposeManagerRoleRequestPolicyChannelRestrictionAndPortableProfiles() throws Exception {
        String catalog = source("interactions/ModernCommandCatalog.java");
        String interactions = source("interactions/ModernInteractions.java");
        String preferences = source("settings/GuildPreferences.java");

        assertTrue(catalog.contains("new SubcommandData(\"request-access\""));
        assertTrue(catalog.contains("new SubcommandData(\"manager-role\""));
        assertTrue(catalog.contains("new SubcommandData(\"voice-channel\""));
        assertTrue(catalog.contains("new SubcommandData(\"permissions\""));
        assertTrue(catalog.contains("new SubcommandData(\"audit\""));
        assertTrue(catalog.contains("new SubcommandData(\"export\""));
        assertTrue(catalog.contains("new SubcommandData(\"import\""));
        assertTrue(interactions.contains("SettingsProfileCodec.encode"));
        assertTrue(interactions.contains("SettingsProfileCodec.decode"));
        assertTrue(preferences.contains("managerRoleId"));
        assertTrue(preferences.contains("musicChannelId"));
        assertTrue(preferences.contains("RequestAccessMode"));
    }

    @Test
    void administrationIsPersistentBoundedAndAudited() throws Exception {
        String repository = source("settings/FileGuildPreferencesRepository.java");
        String policy = source("settings/GuildAdministrationPolicy.java");

        assertTrue(repository.contains("MAX_AUDIT_ENTRIES"));
        assertTrue(repository.contains("updated.size() > MAX_AUDIT_ENTRIES"));
        assertTrue(repository.contains("manager-role"));
        assertTrue(repository.contains("music-channel"));
        assertTrue(repository.contains("request-access"));
        assertTrue(repository.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(policy.contains("preferences.managerRoleId()"));
        assertTrue(policy.contains("Permission.MANAGE_SERVER"));
    }

    @Test
    void requestPolicySeparatesEnqueueRightsFromPlaybackControls() throws Exception {
        String policy = source("commands/music/MusicControlPolicy.java");

        assertTrue(policy.contains("RequestAccessMode.DJ_ONLY"));
        assertTrue(policy.contains("configuredMusicChannelId"));
        assertTrue(policy.contains("preferences.requestAccessMode()"));
        assertTrue(policy.contains("preferences.musicChannelId()"));
        assertTrue(policy.contains("PlaybackAccessMode.VOTE_SKIP"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
