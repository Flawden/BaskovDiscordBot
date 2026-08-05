package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Превращает пользовательский ввод в безопасный LavaPlayer identifier.
 */
@Component
public class MediaQueryResolver {

    public static final String YOUTUBE_SEARCH_PREFIX = "ytsearch:";
    public static final String SOUNDCLOUD_SEARCH_PREFIX = "scsearch:";

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "soundcloud.com",
            "on.soundcloud.com",
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com"
    );

    public String resolve(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Поисковый запрос не может быть пустым");
        }

        String trimmed = input.trim();
        if (!looksLikeUrl(trimmed)) {
            return YOUTUBE_SEARCH_PREFIX + trimmed;
        }

        URI uri = parseUri(trimmed);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        if (!(scheme.equals("https") || scheme.equals("http"))) {
            throw new IllegalArgumentException("Поддерживаются только HTTP/HTTPS ссылки");
        }
        if (uri.getUserInfo() != null || host.isBlank() || !isAllowedHost(host)) {
            throw new IllegalArgumentException("Разрешены только ссылки SoundCloud и YouTube");
        }

        return uri.toASCIIString();
    }

    public MediaProvider provider(String identifier) {
        return MediaProvider.fromIdentifier(identifier);
    }

    private static URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Ссылка имеет неверный формат", exception);
        }
    }

    private static boolean looksLikeUrl(String value) {
        return value.contains("://");
    }

    private static boolean isAllowedHost(String host) {
        return ALLOWED_HOSTS.stream()
                .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }
}
