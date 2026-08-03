package ru.flawden.BascovDiscordBot.commands.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaQueryResolverTest {

    private final MediaQueryResolver resolver = new MediaQueryResolver();

    @Test
    void convertsPlainTextToSoundCloudSearch() {
        assertEquals("scsearch:Sabaton Heart of Iron",
                resolver.resolve("  Sabaton Heart of Iron  "));
    }

    @Test
    void acceptsTrustedMediaUrlsWithoutNetworkRequest() {
        assertEquals(
                "https://soundcloud.com/example/track",
                resolver.resolve("https://soundcloud.com/example/track"));
        assertEquals(
                "https://youtu.be/example",
                resolver.resolve("https://youtu.be/example"));
    }

    @Test
    void rejectsLocalFilesPrivateEndpointsAndCredentialUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("http://127.0.0.1:8080/admin"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("https://user:password@soundcloud.com/track"));
    }
}
