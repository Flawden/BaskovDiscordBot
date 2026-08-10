package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedHomeMusicHubContractTest {

    @Test
    void homeSnapshotIsClientNeutralAndContainsProductSections() throws Exception {
        String snapshot = read("home/HomeSnapshot.java");

        assertTrue(snapshot.contains("record HomeSnapshot"));
        assertTrue(snapshot.contains("List<MixCard> today"));
        assertTrue(snapshot.contains("List<MixCard> forYou"));
        assertTrue(snapshot.contains("List<ThemeCard> themes"));
        assertTrue(snapshot.contains("LibraryCard library"));
        assertTrue(snapshot.contains("List<TrackPreview> recent"));
        assertFalse(snapshot.contains("net.dv8tion"));
        assertFalse(snapshot.contains("EmbedBuilder"));
        assertFalse(snapshot.contains("SlashCommand"));
    }

    @Test
    void homeServiceDependsOnReadPortInsteadOfDiscordInteractions() throws Exception {
        String service = read("home/MusicHomeService.java");
        String port = read("home/MusicHomeReadPort.java");

        assertTrue(service.contains("MusicHomeReadPort source"));
        assertTrue(port.contains("interface MusicHomeReadPort"));
        assertFalse(service.contains("ModernInteractions"));
        assertFalse(service.contains("net.dv8tion"));
        assertFalse(service.contains("EmbedBuilder"));
    }

    @Test
    void discordHomeOnlyRendersSnapshotAndDoesNotOwnRecommendationLogic() throws Exception {
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(interactions.contains("case \"home\" -> home(event)"));
        assertTrue(interactions.contains("musicProductService.home"));
        assertTrue(interactions.contains("HomeSnapshot"));
        assertFalse(method(interactions, "private void home(", "private static String homeMixLines(")
                .contains("recommendationFeedback.tasteProfile"));
        assertFalse(method(interactions, "private void home(", "private static String homeMixLines(")
                .contains("musicLibraryRepository.favorites"));
    }

    @Test
    void homeIsReadOnlyAndDoesNotCreateNewPersistenceOrPlaybackTransport() throws Exception {
        String home = read("home/MusicHomeService.java");
        String adapter = read("home/RuntimeMusicHomeReadAdapter.java");

        assertFalse(home.contains("startStation"));
        assertFalse(home.contains("stopRadio"));
        assertFalse(home.contains("loadItem"));
        assertFalse(adapter.contains("home.tsv"));
        assertFalse(adapter.contains("HomeRepository"));
        assertFalse(adapter.contains("loadItem"));
    }

    @Test
    void topLevelHomeCommandIsPublishedForFutureClientParity() throws Exception {
        String catalog = read("interactions/ModernCommandCatalog.java");
        String readme = Files.readString(Path.of("Readme.md"));

        assertTrue(catalog.contains("Commands.slash(\"home\""));
        assertTrue(readme.contains("/home"));
        assertTrue(readme.contains("HomeSnapshot"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/flawden/BascovDiscordBot").resolve(relative));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        return start >= 0 && end > start ? source.substring(start, end) : "";
    }
}
