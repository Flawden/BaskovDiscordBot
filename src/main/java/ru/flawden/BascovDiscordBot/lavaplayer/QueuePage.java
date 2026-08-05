package ru.flawden.BascovDiscordBot.lavaplayer;

import java.util.List;
import java.util.Objects;

/**
 * Неизменяемая 1-based страница ожидающей музыкальной очереди.
 */
public record QueuePage(
        int number,
        int totalPages,
        int totalItems,
        int firstPosition,
        List<TrackRequest> items) {

    public static final int PAGE_SIZE = 10;

    public QueuePage {
        if (number < 1 || totalPages < 1 || totalItems < 0 || firstPosition < 0) {
            throw new IllegalArgumentException("Invalid queue page metadata");
        }
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static QueuePage of(List<TrackRequest> requests, int requestedPage) {
        List<TrackRequest> snapshot = List.copyOf(Objects.requireNonNull(requests, "requests"));
        int totalItems = snapshot.size();
        int totalPages = Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE);
        int safeRequestedPage = Math.max(1, requestedPage);
        int page = Math.min(safeRequestedPage, totalPages);
        int fromIndex = Math.min((page - 1) * PAGE_SIZE, totalItems);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, totalItems);
        int firstPosition = totalItems == 0 ? 0 : fromIndex + 1;

        return new QueuePage(
                page,
                totalPages,
                totalItems,
                firstPosition,
                snapshot.subList(fromIndex, toIndex));
    }

    public int lastPosition() {
        return items.isEmpty() ? 0 : firstPosition + items.size() - 1;
    }

    public boolean hasPrevious() {
        return number > 1;
    }

    public boolean hasNext() {
        return number < totalPages;
    }
}
