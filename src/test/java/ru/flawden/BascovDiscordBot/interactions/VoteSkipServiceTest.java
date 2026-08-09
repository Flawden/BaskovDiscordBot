package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteSkipServiceTest {

    @Test
    void calculatesCeilingThresholdWithAtLeastOneVote() {
        assertEquals(1, VoteSkipService.requiredVotes(1, 50));
        assertEquals(2, VoteSkipService.requiredVotes(3, 50));
        assertEquals(3, VoteSkipService.requiredVotes(5, 60));
        assertEquals(4, VoteSkipService.requiredVotes(4, 100));
    }

    @Test
    void deduplicatesVotersAndPassesAtThreshold() {
        VoteSkipService service = new VoteSkipService();

        var first = service.vote(10L, "track-a", 1L, 4, 50);
        assertEquals(VoteSkipService.VoteStatus.ACCEPTED, first.status());
        assertEquals(1, first.votes());
        assertTrue(service.hasActiveSession(10L));

        var duplicate = service.vote(10L, "track-a", 1L, 4, 50);
        assertEquals(VoteSkipService.VoteStatus.DUPLICATE, duplicate.status());
        assertEquals(1, duplicate.votes());

        var passed = service.vote(10L, "track-a", 2L, 4, 50);
        assertEquals(VoteSkipService.VoteStatus.PASSED, passed.status());
        assertEquals(2, passed.votes());
        assertFalse(service.hasActiveSession(10L));
    }

    @Test
    void newPlaybackKeyDropsVotesFromPreviousTrack() {
        VoteSkipService service = new VoteSkipService();
        service.vote(10L, "track-a", 1L, 3, 100);

        var replacement = service.vote(10L, "track-b", 2L, 3, 100);

        assertEquals(VoteSkipService.VoteStatus.ACCEPTED, replacement.status());
        assertEquals(1, replacement.votes());
        assertEquals(3, replacement.requiredVotes());
    }

    @Test
    void guildResetRemovesOpenVote() {
        VoteSkipService service = new VoteSkipService();
        service.vote(77L, "track", 1L, 3, 100);

        service.reset(77L);

        assertFalse(service.hasActiveSession(77L));
    }
    @Test
    void snapshotReadsProgressWithoutCastingAnotherVote() {
        VoteSkipService service = new VoteSkipService();
        service.vote(10L, "track-a", 1L, 4, 75);

        var snapshot = service.snapshot(10L, "track-a", 1L, 4, 75);
        var after = service.snapshot(10L, "track-a", 2L, 4, 75);

        assertEquals(1, snapshot.votes());
        assertEquals(3, snapshot.requiredVotes());
        assertEquals(4, snapshot.eligibleListeners());
        assertEquals(75, snapshot.thresholdPercent());
        assertTrue(snapshot.viewerVoted());
        assertFalse(after.viewerVoted());
        assertEquals(1, after.votes());
    }

    @Test
    void snapshotIgnoresVotesFromDifferentPlaybackKey() {
        VoteSkipService service = new VoteSkipService();
        service.vote(10L, "track-a", 1L, 4, 75);

        var snapshot = service.snapshot(10L, "track-b", 1L, 4, 75);

        assertEquals(0, snapshot.votes());
        assertFalse(snapshot.viewerVoted());
        assertEquals(3, snapshot.requiredVotes());
    }

}
