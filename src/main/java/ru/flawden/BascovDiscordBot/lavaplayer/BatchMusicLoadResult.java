package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Итог упорядоченной загрузки плейлиста или replay-набора.
 */
public record BatchMusicLoadResult(
        int requested,
        int started,
        int queued,
        int rejected,
        AudioTrack firstStartedTrack) {

    public BatchMusicLoadResult {
        if (requested < 0 || started < 0 || queued < 0 || rejected < 0) {
            throw new IllegalArgumentException("Batch counters cannot be negative");
        }
        if (started + queued + rejected != requested) {
            throw new IllegalArgumentException("Batch counters must add up to requested");
        }
    }

    public int accepted() {
        return started + queued;
    }
}
