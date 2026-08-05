package ru.flawden.BascovDiscordBot.commands.music;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Определяет фактический media provider по поисковому identifier или track URI.
 */
public enum MediaProvider {
    YOUTUBE("YouTube"),
    SOUNDCLOUD("SoundCloud"),
    HTTP("HTTP"),
    UNKNOWN("Неизвестно");

    private final String label;

    MediaProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MediaProvider fromIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return UNKNOWN;
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ytsearch:") || normalized.startsWith("ytmsearch:")) {
            return YOUTUBE;
        }
        if (normalized.startsWith("scsearch:")) {
            return SOUNDCLOUD;
        }
        return fromUri(identifier);
    }

    public static MediaProvider fromUri(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (isYoutubeHost(host)) {
                return YOUTUBE;
            }
            if (isSoundCloudHost(host)) {
                return SOUNDCLOUD;
            }
            if (scheme.equals("http") || scheme.equals("https")) {
                return HTTP;
            }
        } catch (URISyntaxException ignored) {
            return UNKNOWN;
        }
        return UNKNOWN;
    }

    static boolean isYoutubeHost(String host) {
        return host.equals("youtu.be")
                || host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com")
                || host.endsWith(".youtube-nocookie.com");
    }

    static boolean isSoundCloudHost(String host) {
        return host.equals("soundcloud.com")
                || host.endsWith(".soundcloud.com")
                || host.equals("on.soundcloud.com")
                || host.endsWith(".on.soundcloud.com");
    }
}
