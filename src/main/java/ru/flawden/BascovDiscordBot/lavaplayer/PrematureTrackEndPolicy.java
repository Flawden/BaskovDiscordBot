package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Выявляет SoundCloud preview/обрезанные потоки, которые LavaPlayer помечает как
 * {@code FINISHED}, хотя позиция воспроизведения ещё далеко от заявленной длины.
 */
final class PrematureTrackEndPolicy {

    static final long MIN_EXPECTED_DURATION_MILLIS = 90_000L;
    static final long MAX_PREVIEW_POSITION_MILLIS = 45_000L;
    static final long MIN_MISSING_TAIL_MILLIS = 60_000L;

    private PrematureTrackEndPolicy() {
    }

    static boolean isPremature(AudioTrack track, long elapsedMillis) {
        if (track == null || track.getInfo() == null) {
            return false;
        }

        long duration = Math.max(0L, track.getDuration());
        long position = Math.max(0L, track.getPosition());
        long missingTail = Math.max(0L, duration - position);
        return duration >= MIN_EXPECTED_DURATION_MILLIS
                && Math.max(0L, elapsedMillis) <= MAX_PREVIEW_POSITION_MILLIS
                && position <= MAX_PREVIEW_POSITION_MILLIS
                && missingTail >= MIN_MISSING_TAIL_MILLIS;
    }

    static String diagnostic(AudioTrack track, long elapsedMillis) {
        long position = track == null ? 0L : Math.max(0L, track.getPosition());
        long duration = track == null ? 0L : Math.max(0L, track.getDuration());
        return "premature FINISHED at " + position + "ms of " + duration
                + "ms; elapsed=" + Math.max(0L, elapsedMillis) + "ms";
    }
}
