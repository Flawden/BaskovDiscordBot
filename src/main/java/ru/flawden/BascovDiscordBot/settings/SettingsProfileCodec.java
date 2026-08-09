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

    static final String PREFIX_V1 = "BASKOV_SETTINGS_V1.";
    static final String PREFIX_V2 = "BASKOV_SETTINGS_V2.";

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
                "moderatorRole=" + Long.toUnsignedString(preferences.moderatorRoleId()),
                "musicChannel=" + Long.toUnsignedString(preferences.musicChannelId()),
                "voteSkipPercent=" + preferences.voteSkipPercent(),
                "requesterQueueLimit=" + preferences.requesterQueueLimit());
        return PREFIX_V2 + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    public static GuildPreferences decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Неизвестный формат профиля настроек");
        }
        boolean legacyV1 = encoded.startsWith(PREFIX_V1);
        boolean currentV2 = encoded.startsWith(PREFIX_V2);
        if (!legacyV1 && !currentV2) {
            throw new IllegalArgumentException("Неизвестный формат профиля настроек");
        }
        String prefix = legacyV1 ? PREFIX_V1 : PREFIX_V2;
        String payload = encoded.substring(prefix.length()).trim();
        if (payload.isEmpty() || payload.length() > 3072) {
            throw new IllegalArgumentException("Профиль настроек пуст или слишком велик");
        }

        final String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Профиль настроек повреждён", exception);
        }

        Map<String, String> values = parseValues(decoded);
        if (legacyV1) {
            requireExactly(values, new String[] {
                    "volume", "repeat", "playbackAccess", "requestAccess",
                    "djRole", "managerRole", "musicChannel", "voteSkipPercent"
            });
            try {
                return new GuildPreferences(
                        Integer.parseInt(values.get("volume")),
                        RepeatMode.valueOf(values.get("repeat")),
                        PlaybackAccessMode.valueOf(values.get("playbackAccess")),
                        RequestAccessMode.valueOf(values.get("requestAccess")),
                        Long.parseUnsignedLong(values.get("djRole")),
                        Long.parseUnsignedLong(values.get("managerRole")),
                        0L,
                        Long.parseUnsignedLong(values.get("musicChannel")),
                        Integer.parseInt(values.get("voteSkipPercent")),
                        0);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Профиль настроек содержит недопустимые значения", exception);
            }
        }

        requireExactly(values, new String[] {
                "volume", "repeat", "playbackAccess", "requestAccess",
                "djRole", "managerRole", "moderatorRole", "musicChannel",
                "voteSkipPercent", "requesterQueueLimit"
        });
        try {
            return new GuildPreferences(
                    Integer.parseInt(values.get("volume")),
                    RepeatMode.valueOf(values.get("repeat")),
                    PlaybackAccessMode.valueOf(values.get("playbackAccess")),
                    RequestAccessMode.valueOf(values.get("requestAccess")),
                    Long.parseUnsignedLong(values.get("djRole")),
                    Long.parseUnsignedLong(values.get("managerRole")),
                    Long.parseUnsignedLong(values.get("moderatorRole")),
                    Long.parseUnsignedLong(values.get("musicChannel")),
                    Integer.parseInt(values.get("voteSkipPercent")),
                    Integer.parseInt(values.get("requesterQueueLimit")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Профиль настроек содержит недопустимые значения", exception);
        }
    }

    private static Map<String, String> parseValues(String decoded) {
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
        return values;
    }

    private static void requireExactly(Map<String, String> values, String[] required) {
        if (values.size() != required.length) {
            throw new IllegalArgumentException("Профиль настроек содержит неизвестные или отсутствующие поля");
        }
        for (String key : required) {
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Профиль настроек не содержит поле: " + key);
            }
        }
    }
}
