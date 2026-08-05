package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Путь к постоянным плейлистам и истории воспроизведения.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.music-library")
public class MusicLibraryProperties {

    private Path file = Path.of("data", "music-library.tsv");

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = Objects.requireNonNull(file, "discord-bot.music-library.file");
    }
}
