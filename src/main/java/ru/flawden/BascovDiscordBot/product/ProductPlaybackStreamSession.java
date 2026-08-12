package ru.flawden.BascovDiscordBot.product;

import java.io.IOException;
import java.io.OutputStream;

/** One foreground mobile playback stream prepared by the runtime transport adapter. */
public interface ProductPlaybackStreamSession extends AutoCloseable {

    long durationMillis();

    default String artworkUrl() {
        return "";
    }

    void writeOgg(OutputStream output) throws IOException;

    @Override
    void close();
}
