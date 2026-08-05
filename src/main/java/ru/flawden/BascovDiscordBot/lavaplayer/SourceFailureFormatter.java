package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Формирует компактную причину media/source failure для /status без потери
 * глубинного IOException/HTTP-кода под FriendlyException.
 */
final class SourceFailureFormatter {

    private static final int MAX_VALUE_LENGTH = 240;

    private SourceFailureFormatter() {
    }

    static String describe(AudioTrack track, Throwable failure) {
        StringBuilder result = new StringBuilder(deepestMessage(failure));
        if (track != null) {
            result.append("; position=")
                    .append(Math.max(0L, track.getPosition()))
                    .append('/')
                    .append(Math.max(0L, track.getDuration()))
                    .append("ms");
            if (track.getInfo() != null) {
                String reference = firstNonBlank(track.getInfo().uri, track.getInfo().identifier);
                if (reference != null) {
                    result.append("; media=").append(compact(reference));
                }
            }
        }
        return compact(result.toString());
    }

    static String describe(String identifier, Throwable failure) {
        String result = deepestMessage(failure);
        if (identifier != null && !identifier.isBlank()) {
            result += "; media=" + identifier;
        }
        return compact(result);
    }

    private static String deepestMessage(Throwable failure) {
        if (failure == null) {
            return "unknown source failure";
        }
        Throwable current = failure;
        Throwable deepestWithMessage = failure;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                deepestWithMessage = current;
            }
            current = current.getCause();
        }
        String type = deepestWithMessage.getClass().getSimpleName();
        String message = deepestWithMessage.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ": " + message;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String compact(String value) {
        String compact = value == null
                ? "unknown source failure"
                : value.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= MAX_VALUE_LENGTH
                ? compact
                : compact.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }
}
