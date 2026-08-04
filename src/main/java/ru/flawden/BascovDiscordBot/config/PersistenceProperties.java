package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Путь к долговременному хранилищу настроек Discord-серверов.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.persistence")
public class PersistenceProperties {

    private Path file = Path.of("data", "guild-settings.properties");

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = Objects.requireNonNull(file, "discord-bot.persistence.file");
    }
}
