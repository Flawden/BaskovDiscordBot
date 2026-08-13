package ru.flawden.BascovDiscordBot.product.api;

import java.util.List;

/** JSON DTOs for bounded authenticated listening/taste feedback ingestion. */
public final class ProductTasteSignalApiRequest {

    public static final int MAX_EVENTS_PER_BATCH = 50;

    private ProductTasteSignalApiRequest() {
    }

    public enum Type {
        PLAY,
        COMPLETED,
        REPLAY,
        QUICK_SKIP,
        STOP_EARLY,
        FAVORITE_ADD,
        FAVORITE_REMOVE
    }

    public enum Source {
        LOCAL,
        REMOTE
    }

    public record Event(
            Type type,
            Source source,
            String stableKey,
            String artist,
            String title,
            Double completionRatio) {
    }

    public record Batch(List<Event> events) {
        public Batch {
            events = List.copyOf(events == null ? List.of() : events);
            if (events.isEmpty()) {
                throw new IllegalArgumentException("taste event batch cannot be empty");
            }
            if (events.size() > MAX_EVENTS_PER_BATCH) {
                throw new IllegalArgumentException("taste event batch cannot exceed " + MAX_EVENTS_PER_BATCH);
            }
        }
    }
}
