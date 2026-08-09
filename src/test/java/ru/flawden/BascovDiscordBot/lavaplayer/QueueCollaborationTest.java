package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueCollaborationTest {

    @Test
    void groupsContributorsAndKeepsGlobalPositions() {
        TrackRequest mine1 = request(42L, "Me", "Mine 1", 1);
        TrackRequest other = request(7L, "Other", "Other", 2);
        TrackRequest mine2 = request(42L, "Me", "Mine 2", 3);

        QueueCollaboration.Summary summary = QueueCollaboration.summarize(
                List.of(mine1, other, mine2), 42L);

        assertEquals(3, summary.totalTracks());
        assertEquals(Duration.ofMinutes(6).toMillis(), summary.totalDurationMillis());
        assertEquals(List.of(1, 3), summary.ownedTracks().stream()
                .map(QueueCollaboration.OwnedTrack::globalPosition)
                .toList());
        assertEquals(Duration.ofMinutes(4).toMillis(), summary.ownDurationMillis());
        assertEquals(42L, summary.contributors().get(0).userId());
        assertEquals(List.of(1, 3), summary.contributors().get(0).positions());
    }

    @Test
    void ranksByTrackCountThenDuration() {
        QueueCollaboration.Summary summary = QueueCollaboration.summarize(List.of(
                request(1L, "One", "A", 1),
                request(2L, "Two", "B", 4),
                request(1L, "One", "C", 1),
                request(3L, "Three", "D", 10),
                request(2L, "Two", "E", 4)), 0L);

        assertEquals(List.of(2L, 1L, 3L), summary.contributors().stream()
                .map(QueueCollaboration.Contributor::userId)
                .toList());
    }

    @Test
    void unknownRequestersAreGroupedByDisplayName() {
        QueueCollaboration.Summary summary = QueueCollaboration.summarize(List.of(
                request(0L, "Legacy", "A", 1),
                request(0L, "Legacy", "B", 1)), 0L);

        assertEquals(1, summary.contributors().size());
        assertEquals(2, summary.contributors().get(0).trackCount());
        assertEquals("Legacy", summary.contributors().get(0).discordLabel());
    }

    private static TrackRequest request(long userId, String name, String title, int minutes) {
        AudioTrack track = mock(AudioTrack.class);
        when(track.getDuration()).thenReturn(Duration.ofMinutes(minutes).toMillis());
        return TrackRequest.create(track, new TrackRequester(userId, name));
    }
}
