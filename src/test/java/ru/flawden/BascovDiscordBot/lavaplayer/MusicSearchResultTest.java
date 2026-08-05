package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MusicSearchResultTest {

    @Test
    void resultCopiesCandidateList() {
        AudioTrack track = mock(AudioTrack.class);
        List<AudioTrack> mutable = new ArrayList<>(List.of(track));

        MusicSearchResult result = MusicSearchResult.found("ytsearch:test", mutable);
        mutable.clear();

        assertEquals(MusicSearchResult.Status.FOUND, result.status());
        assertEquals(List.of(track), result.tracks());
        assertThrows(UnsupportedOperationException.class, () -> result.tracks().clear());
    }

    @Test
    void emptyResultNormalizesNullValues() {
        MusicSearchResult result = new MusicSearchResult(
                MusicSearchResult.Status.NO_MATCHES,
                null,
                null);

        assertEquals("", result.query());
        assertEquals(List.of(), result.tracks());
    }
}
