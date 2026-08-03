package ru.flawden.BascovDiscordBot.interactions;

import java.util.OptionalLong;

/**
 * Разбирает позицию вида SS, MM:SS или HH:MM:SS.
 */
public final class PlaybackPositionParser {

    private PlaybackPositionParser() {
    }

    public static OptionalLong parseMillis(String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }

        String[] parts = input.trim().split(":", -1);
        if (parts.length < 1 || parts.length > 3) {
            return OptionalLong.empty();
        }

        long[] values = new long[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isBlank() || !parts[index].matches("\\d{1,3}")) {
                    return OptionalLong.empty();
                }
                values[index] = Long.parseLong(parts[index]);
            }
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }

        long seconds;
        if (parts.length == 1) {
            seconds = values[0];
        } else if (parts.length == 2) {
            if (values[1] >= 60) {
                return OptionalLong.empty();
            }
            seconds = values[0] * 60 + values[1];
        } else {
            if (values[1] >= 60 || values[2] >= 60) {
                return OptionalLong.empty();
            }
            seconds = values[0] * 3600 + values[1] * 60 + values[2];
        }

        try {
            return OptionalLong.of(Math.multiplyExact(seconds, 1000L));
        } catch (ArithmeticException exception) {
            return OptionalLong.empty();
        }
    }
}
