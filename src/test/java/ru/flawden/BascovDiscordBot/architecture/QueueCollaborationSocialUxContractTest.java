package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueCollaborationSocialUxContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void requesterSelfServiceUsesOwnershipAndRevisionGuards() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(scheduler.contains("removeRequesterAt("));
        assertTrue(scheduler.indexOf("staleRevisionResult(expectedRevision)")
                < scheduler.indexOf("QueueMutationStatus.NOT_OWNER"));
        assertTrue(interactions.contains("Это чужой трек"));
        assertTrue(interactions.contains("/queue-manage mine"));
    }

    @Test
    void collaborationProjectionStaysReadOnlyAndRequesterAware() throws IOException {
        String collaboration = read("lavaplayer/QueueCollaboration.java");

        assertTrue(collaboration.contains("public static Summary summarize"));
        assertTrue(collaboration.contains("requester.userId()"));
        assertTrue(collaboration.contains("globalPosition"));
        assertTrue(collaboration.contains("List.copyOf"));
    }

    @Test
    void queueUiPublishesMineCommunityAndVoteStatusButtons() throws IOException {
        String controls = read("interactions/MusicControls.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(controls.contains("QUEUE_MINE"));
        assertTrue(controls.contains("QUEUE_COMMUNITY"));
        assertTrue(controls.contains("VOTE_STATUS"));
        assertTrue(interactions.contains("MusicEmbeds.personalQueue"));
        assertTrue(interactions.contains("MusicEmbeds.queueCommunity"));
        assertTrue(interactions.contains("replyVoteStatus"));
    }

    @Test
    void voteStatusSnapshotDoesNotCallVote() throws IOException {
        String service = read("interactions/VoteSkipService.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(service.contains("public VoteSnapshot snapshot("));
        assertTrue(service.contains("boolean viewerVoted"));
        assertTrue(interactions.contains("voteSkipService.snapshot("));
        assertTrue(interactions.contains("ещё не отдан"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
