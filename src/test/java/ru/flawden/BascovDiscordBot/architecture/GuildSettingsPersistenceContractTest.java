package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildSettingsPersistenceContractTest {

    private static final Path ROOT = Path.of(".");
    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void settingsAreAtomicallyPersistedAndLoadedByNewSessions() throws IOException {
        String repository = read("settings/FileGuildPreferencesRepository.java");
        String playerManager = read("lavaplayer/PlayerManager.java");
        String guildManager = read("lavaplayer/GuildMusicManager.java");

        assertTrue(repository.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(repository.contains("Long.toUnsignedString(entry.getKey())"));
        assertTrue(playerManager.contains("preferencesRepository.get(guildId)"));
        assertTrue(guildManager.contains("initialPreferences.volume()"));
        assertTrue(guildManager.contains("initialPreferences.repeatMode()"));
    }

    @Test
    void slashCatalogExposesAdminManagedSettings() throws IOException {
        String catalog = read("interactions/ModernCommandCatalog.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("Commands.slash(\"settings\""));
        assertTrue(catalog.contains("new SubcommandData(\"show\""));
        assertTrue(catalog.contains("new SubcommandData(\"volume\""));
        assertTrue(catalog.contains("new SubcommandData(\"repeat\""));
        assertTrue(catalog.contains("new SubcommandData(\"reset\""));
        assertTrue(interactions.contains("Permission.MANAGE_SERVER"));
    }

    @Test
    void dockerKeepsSettingsInAStableNamedVolume() throws IOException {
        String dockerfile = Files.readString(ROOT.resolve("Dockerfile"));
        String productionCompose = Files.readString(ROOT.resolve("deploy/docker-compose.yml"));

        assertTrue(dockerfile.contains("/app/data"));
        assertTrue(productionCompose.contains("bot-data:/app/data"));
        assertTrue(productionCompose.contains("DISCORD_BOT_PERSISTENCE_FILE"));
    }

    @Test
    void termuxReleaseGuideKeepsAllSafetyGates() throws IOException {
        String termux = Files.readString(ROOT.resolve("docs/TERMUX-RELEASE.md"));

        assertTrue(termux.contains("/storage/emulated/0/Download/"));
        assertTrue(termux.contains("sha256sum"));
        assertTrue(termux.contains("git apply --check"));
        assertTrue(termux.contains("clean verify"));
        assertTrue(termux.contains("git push origin master"));
        assertTrue(termux.contains("git apply -R"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
