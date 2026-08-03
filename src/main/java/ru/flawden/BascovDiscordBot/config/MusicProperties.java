package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ограничения и таймауты музыкальных сессий.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.music")
public class MusicProperties {

    private int maxQueueSize = 100;
    private Duration maxTrackDuration = Duration.ofHours(4);
    private Duration idleDisconnectTimeout = Duration.ofMinutes(5);

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        if (maxQueueSize < 1 || maxQueueSize > 1_000) {
            throw new IllegalArgumentException("discordBot.music.maxQueueSize must be between 1 and 1000");
        }
        this.maxQueueSize = maxQueueSize;
    }

    public Duration getMaxTrackDuration() {
        return maxTrackDuration;
    }

    public void setMaxTrackDuration(Duration maxTrackDuration) {
        if (maxTrackDuration == null || maxTrackDuration.isNegative() || maxTrackDuration.isZero()) {
            throw new IllegalArgumentException("discordBot.music.maxTrackDuration must be positive");
        }
        this.maxTrackDuration = maxTrackDuration;
    }

    public Duration getIdleDisconnectTimeout() {
        return idleDisconnectTimeout;
    }

    public void setIdleDisconnectTimeout(Duration idleDisconnectTimeout) {
        if (idleDisconnectTimeout == null || idleDisconnectTimeout.isNegative()) {
            throw new IllegalArgumentException("discordBot.music.idleDisconnectTimeout cannot be negative");
        }
        this.idleDisconnectTimeout = idleDisconnectTimeout;
    }
}
