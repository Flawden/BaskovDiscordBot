package ru.flawden.BascovDiscordBot.library;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Убирает файловую запись истории с LavaPlayer/JDA audio callback thread.
 */
@Slf4j
@Component
public class PlaybackHistoryRecorder {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private final MusicLibraryRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "baskov-playback-history");
        thread.setDaemon(true);
        return thread;
    });

    public PlaybackHistoryRecorder(MusicLibraryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void record(long guildId, TrackRequest request) {
        final StoredTrack track;
        try {
            track = StoredTrack.from(request).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Ignoring unreplayable playback history entry for guild {}", guildId, exception);
            return;
        }
        if (track == null) {
            return;
        }
        try {
            executor.execute(() -> persist(guildId, track));
        } catch (RejectedExecutionException exception) {
            log.debug("Ignoring playback history entry during shutdown: guild={}, track={}",
                    guildId,
                    track.title());
        }
    }

    private void persist(long guildId, StoredTrack track) {
        try {
            repository.recordHistory(guildId, track);
        } catch (RuntimeException exception) {
            log.error("Cannot persist playback history for guild {}", guildId, exception);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
