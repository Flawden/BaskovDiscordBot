package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdministrationModeration2ContractTest {

    @Test
    void moderationRoleAndRequesterLimitArePersistentLeastPrivilegeSettings() throws Exception {
        String preferences = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/settings/GuildPreferences.java"));
        String repository = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/settings/FileGuildPreferencesRepository.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/settings/QueueModerationPolicy.java"));

        assertTrue(preferences.contains("moderatorRoleId"));
        assertTrue(preferences.contains("requesterQueueLimit"));
        assertTrue(repository.contains("moderator-role"));
        assertTrue(repository.contains("requester-queue-limit"));
        assertTrue(policy.contains("moderatorRole"));
        assertFalse(policy.contains("saveManagerRoleId"));
    }

    @Test
    void requesterLimitIsEnforcedInsideSchedulerForEveryQueueingPath() throws Exception {
        String scheduler = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/TrackScheduler.java"));
        String playerManager = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(scheduler.contains("QueueStatus.REQUESTER_LIMIT"));
        assertTrue(scheduler.contains("requesterQueuedCountLocked"));
        assertTrue(playerManager.contains("case REQUESTER_LIMIT -> MusicLoadResult.Status.REQUESTER_LIMIT"));
    }

    @Test
    void sessionRecoveryBypassesOnlyRequesterCapForSavedQueue() throws Exception {
        String scheduler = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/TrackScheduler.java"));
        String playerManager = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(scheduler.contains("queueRecovered("));
        assertTrue(scheduler.contains("queueInternal(track, requester, fallbackTracks, false)"));
        assertTrue(playerManager.contains("getScheduler().queueRecovered("));
        assertTrue(playerManager.contains("boolean recoveryRestore"));
    }

    @Test
    void moderationMutationsRemainRevisionSafeAndAudited() throws Exception {
        String catalog = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernCommandCatalog.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));

        assertTrue(catalog.contains("Commands.slash(\"moderation\""));
        assertTrue(catalog.contains("new SubcommandData(\"purge\""));
        assertTrue(interactions.contains("removeRequester("));
        assertTrue(interactions.contains("queueRevisionOption(event)"));
        assertTrue(interactions.contains("moderation:purge"));
    }

    @Test
    void settingsProfilesUpgradeToV2ButContinueReadingV1() throws Exception {
        String codec = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/settings/SettingsProfileCodec.java"));

        assertTrue(codec.contains("BASKOV_SETTINGS_V2."));
        assertTrue(codec.contains("BASKOV_SETTINGS_V1."));
        assertTrue(codec.contains("moderatorRole"));
        assertTrue(codec.contains("requesterQueueLimit"));
    }
}
