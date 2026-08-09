package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Persistent recommendation outcome journal consumed by the personal ranking model.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.recommendation-feedback")
public class RecommendationFeedbackProperties {

    private Path file = Path.of("data", "recommendation-feedback.tsv");

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = Objects.requireNonNull(file, "discord-bot.recommendation-feedback.file");
    }
}
