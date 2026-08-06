package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DjRoleVotingContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void publishesVoteSkipAndPersistentDjSettings() throws Exception {
        String catalog = source("interactions/ModernCommandCatalog.java");
        String repository = source("settings/FileGuildPreferencesRepository.java");

        assertTrue(catalog.contains("Commands.slash(\"voteskip\""));
        assertTrue(catalog.contains("new SubcommandData(\"access\""));
        assertTrue(catalog.contains("new SubcommandData(\"dj-role\""));
        assertTrue(catalog.contains("new SubcommandData(\"vote-threshold\""));
        assertTrue(repository.contains("vote-skip-percent"));
        assertTrue(repository.contains("dj-role"));
        assertTrue(repository.contains("PlaybackAccessMode"));
    }

    @Test
    void votesAreBoundedUniqueAndTrackScoped() throws Exception {
        String voting = source("interactions/VoteSkipService.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(voting.contains("Set<Long> voterUserIds"));
        assertTrue(voting.contains("requiredVotes"));
        assertTrue(voting.contains("SESSION_TTL"));
        assertTrue(interactions.contains("playbackVoteKey(current)"));
        assertTrue(interactions.contains("eligibleHumanListeners(guild)"));
        assertTrue(interactions.contains("voteSkipService.reset"));
    }

    @Test
    void privilegedAndDjControlsRemainDirectWhileListenersVote() throws Exception {
        String policy = source("commands/music/MusicControlPolicy.java");

        assertTrue(policy.contains("SkipAccess.DIRECT"));
        assertTrue(policy.contains("SkipAccess.VOTE"));
        assertTrue(policy.contains("member.getRoles()"));
        assertTrue(policy.contains("Permission.MANAGE_SERVER"));
        assertTrue(policy.contains("PlaybackAccessMode.VOTE_SKIP"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
