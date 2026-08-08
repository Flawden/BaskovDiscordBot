package ru.flawden.BascovDiscordBot.settings;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Переносимый текстовый профиль guild settings без внешней JSON-зависимости.
 */
public final class SettingsProfileCodec {

    static final String PREFIX = "BASKOV_SETTINGS_V1.";

    private SettingsProfileCodec() {
    }

    public static String encode(GuildPreferences preferences) {
        String body = String.join("\n",
                "volume=" + preferences.volume(),
                "repeat=" + preferences.repeatMode().name(),
                "playbackAccess=" + preferences.accessMode().name(),
                "requestAccess=" + preferences.requestAccessMode().name(),
                "djRole=" + Long.toUnsignedString(preferences.djRoleId()),
                "managerRole=" + Long.toUnsignedString(preferences.managerRoleId()),
                "musicChannel=" + Long.toUnsignedString(preferences.musicChannelId()),
                "voteSkipPercent=" + preferences.voteSkipPercent());
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    public static GuildPreferences decode(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Неизвестный формат профиля настроек");
        }
        String payload = encoded.substring(PREFIX.length()).trim();
        if (payload.isEmpty() || payload.length() > 2048) {
            throw new IllegalArgumentException("Профиль настроек пуст или слишком велик");
        }

        final String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Профиль настроек повреждён", exception);
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String line : decoded.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalArgumentException("Профиль настроек содержит повреждённую строку");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Профиль настроек содержит повторяющийся ключ: " + key);
            }
        }

        String[] required = {
                "volume", "repeat", "playbackAccess", "requestAccess",
                "djRole", "managerRole", "musicChannel", "voteSkipPercent"
        };
        if (values.size() != required.length) {
            throw new IllegalArgumentException("Профиль настроек содержит неизвестные или отсутствующие поля");
        }
        for (String key : required) {
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Профиль настроек не содержит поле: " + key);
            }
        }

        try {
            return new GuildPreferences(
                    Integer.parseInt(values.get("volume")),
                    RepeatMode.valueOf(values.get("repeat")),
                    PlaybackAccessMode.valueOf(values.get("playbackAccess")),
                    RequestAccessMode.valueOf(values.get("requestAccess")),
                    Long.parseUnsignedLong(values.get("djRole")),
                    Long.parseUnsignedLong(values.get("managerRole")),
                    Long.parseUnsignedLong(values.get("musicChannel")),
                    Integer.parseInt(values.get("voteSkipPercent")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Профиль настроек содержит недопустимые значения", exception);
        }
    }
}
