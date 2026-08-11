package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.lavaplayer.ExternalAudioTrackStream;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.playback.PlaybackClientCapabilities;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime adapter that remuxes shared LavaPlayer Opus packets into an authenticated Ogg stream. */
@Component
public class RuntimeProductPlaybackStreamAdapter implements ProductPlaybackStreamPort {

    static final int MAX_CONCURRENT_STREAMS = 4;
    private static final long EMPTY_FRAME_SLEEP_MILLIS = 5L;

    private final PlayerManager playerManager;
    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_STREAMS, true);

    public RuntimeProductPlaybackStreamAdapter(PlayerManager playerManager) {
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
    }

    @Override
    public ProductPlaybackStreamSession open(long guildId, long userId, TrackIdentity track) {
        if (!permits.tryAcquire()) {
            throw new ProductPlaybackUnavailableException("Too many concurrent mobile playback streams");
        }
        try {
            ExternalAudioTrackStream stream = playerManager.openExternalPlayback(
                            Objects.requireNonNull(track, "track"),
                            PlaybackClientCapabilities.android(Set.of(
                                    MediaProvider.YOUTUBE,
                                    MediaProvider.SOUNDCLOUD)))
                    .join();
            return new Session(stream, permits);
        } catch (CompletionException exception) {
            permits.release();
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new ProductPlaybackUnavailableException(
                    "Unable to prepare mobile playback: " + safeMessage(cause), cause);
        } catch (RuntimeException exception) {
            permits.release();
            throw exception;
        }
    }

    private static String safeMessage(Throwable cause) {
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? "playback source unavailable" : message;
    }

    private static final class Session implements ProductPlaybackStreamSession {
        private final ExternalAudioTrackStream stream;
        private final Semaphore permits;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(ExternalAudioTrackStream stream, Semaphore permits) {
            this.stream = Objects.requireNonNull(stream, "stream");
            this.permits = Objects.requireNonNull(permits, "permits");
        }

        @Override
        public long durationMillis() {
            return stream.durationMillis();
        }

        @Override
        public void writeOgg(OutputStream output) throws IOException {
            Objects.requireNonNull(output, "output");
            OggOpusWriter writer = new OggOpusWriter(output);
            try {
                while (!closed.get() && stream.active()) {
                    byte[] packet = stream.pollPacket();
                    if (packet == null) {
                        sleepBriefly();
                        continue;
                    }
                    writer.packet(packet);
                }
                writer.finish();
            } finally {
                close();
            }
        }

        private static void sleepBriefly() throws IOException {
            try {
                Thread.sleep(EMPTY_FRAME_SLEEP_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Mobile playback stream interrupted", exception);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                stream.close();
                permits.release();
            }
        }
    }
}
