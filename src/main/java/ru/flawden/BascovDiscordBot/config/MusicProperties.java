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
    private int defaultVolume = 100;
    private int maxVolume = 150;

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        if (maxQueueSize < 1 || maxQueueSize > 1_000) {
            throw new IllegalArgumentException("discord-bot.music.maxQueueSize must be between 1 and 1000");
        }
        this.maxQueueSize = maxQueueSize;
    }

    public Duration getMaxTrackDuration() {
        return maxTrackDuration;
    }

    public void setMaxTrackDuration(Duration maxTrackDuration) {
        if (maxTrackDuration == null || maxTrackDuration.isNegative() || maxTrackDuration.isZero()) {
            throw new IllegalArgumentException("discord-bot.music.maxTrackDuration must be positive");
        }
        this.maxTrackDuration = maxTrackDuration;
    }

    public int getDefaultVolume() {
        return defaultVolume;
    }

    public void setDefaultVolume(int defaultVolume) {
        if (defaultVolume < 0 || defaultVolume > maxVolume) {
            throw new IllegalArgumentException("discord-bot.music.defaultVolume must be between 0 and maxVolume");
        }
        this.defaultVolume = defaultVolume;
    }

    public int getMaxVolume() {
        return maxVolume;
    }

    public void setMaxVolume(int maxVolume) {
        if (maxVolume < 1 || maxVolume > 500) {
            throw new IllegalArgumentException("discord-bot.music.maxVolume must be between 1 and 500");
        }
        this.maxVolume = maxVolume;
        if (defaultVolume > maxVolume) {
            defaultVolume = maxVolume;
        }
    }

    public Duration getIdleDisconnectTimeout() {
        return idleDisconnectTimeout;
    }

    public void setIdleDisconnectTimeout(Duration idleDisconnectTimeout) {
        if (idleDisconnectTimeout == null || idleDisconnectTimeout.isNegative()) {
            throw new IllegalArgumentException("discord-bot.music.idleDisconnectTimeout cannot be negative");
        }
        this.idleDisconnectTimeout = idleDisconnectTimeout;
    }
}
