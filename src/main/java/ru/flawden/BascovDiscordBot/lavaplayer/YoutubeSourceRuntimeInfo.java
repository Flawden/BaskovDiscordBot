package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Закреплённая runtime-идентичность отдельного modern YouTube source.
 */
public final class YoutubeSourceRuntimeInfo {

    public static final String ENGINE = "lavalink-devs/youtube-source";
    public static final String VERSION = "1.18.2";
    public static final String CLIENTS = "MUSIC,ANDROID_VR,WEB,WEBEMBEDDED";
    public static final String STARTUP_MARKER = "Modern YouTube source ready:";

    private YoutubeSourceRuntimeInfo() {
    }

    public static String statusLabel() {
        return "youtube-source " + VERSION;
    }
}
