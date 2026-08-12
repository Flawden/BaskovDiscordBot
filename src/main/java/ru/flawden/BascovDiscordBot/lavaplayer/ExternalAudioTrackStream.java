package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated foreground stream backed by a dedicated LavaPlayer AudioPlayer.
 * The returned packets are the configured Discord Opus packets and never touch a guild voice session.
 */
public final class ExternalAudioTrackStream implements AutoCloseable {

    private final AudioPlayer player;
    private final long durationMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    ExternalAudioTrackStream(AudioPlayer player, AudioTrack track) {
        this.player = Objects.requireNonNull(player, "player");
        AudioTrack safeTrack = Objects.requireNonNull(track, "track");
        this.durationMillis = Math.max(0L, safeTrack.getDuration());
    }

    public long durationMillis() {
        return durationMillis;
    }

    public long seekTo(long positionMillis) {
        if (positionMillis < 0L) {
            throw new IllegalArgumentException("positionMillis must not be negative");
        }
        AudioTrack current = player.getPlayingTrack();
        if (closed.get() || current == null) {
            throw new IllegalStateException("External playback stream is not active");
        }
        long maximum = Math.max(0L, durationMillis - 1L);
        long effectivePosition = Math.min(positionMillis, maximum);
        current.setPosition(effectivePosition);
        return effectivePosition;
    }

    public boolean active() {
        return !closed.get() && player.getPlayingTrack() != null;
    }

    /** Returns one encoded Opus packet, or {@code null} when no frame is ready yet. */
    public byte[] pollPacket() {
        if (closed.get()) {
            return null;
        }
        var frame = player.provide();
        return frame == null || frame.getData() == null
                ? null
                : Arrays.copyOf(frame.getData(), frame.getData().length);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            player.stopTrack();
            player.destroy();
        }
    }
}
