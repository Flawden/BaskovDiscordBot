package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.net.URI;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated foreground stream backed by a dedicated LavaPlayer AudioPlayer.
 * The returned packets are the configured Discord Opus packets and never touch a guild voice session.
 */
public final class ExternalAudioTrackStream implements AutoCloseable {

    private final AudioPlayer player;
    private final AudioTrack sourceTrack;
    private final long durationMillis;
    private final String artworkUrl;
    private final AtomicBoolean closed = new AtomicBoolean();

    ExternalAudioTrackStream(AudioPlayer player, AudioTrack track) {
        this.player = Objects.requireNonNull(player, "player");
        AudioTrack safeTrack = Objects.requireNonNull(track, "track");
        this.sourceTrack = safeTrack;
        this.durationMillis = Math.max(0L, safeTrack.getDuration());
        var info = safeTrack.getInfo();
        this.artworkUrl = normalizeArtworkUrl(info == null ? null : info.artworkUrl);
    }

    public long durationMillis() {
        return durationMillis;
    }


    public String artworkUrl() {
        return artworkUrl;
    }

    /** Durable provider-backed descriptor used when a mobile-selected track is saved to a shared playlist. */
    public Optional<StoredTrack> storedTrack(long requesterUserId, String requesterDisplayName) {
        return StoredTrack.from(sourceTrack, requesterUserId, requesterDisplayName);
    }

    static String normalizeArtworkUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                return "";
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
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
