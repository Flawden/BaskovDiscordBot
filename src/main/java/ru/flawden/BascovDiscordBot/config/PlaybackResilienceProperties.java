package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runtime-only playback provider resilience settings.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.playback-resilience")
public class PlaybackResilienceProperties {

    private int failureThreshold = 3;
    private Duration cooldown = Duration.ofSeconds(90);

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        if (failureThreshold < 1 || failureThreshold > 10) {
            throw new IllegalArgumentException(
                    "discord-bot.playback-resilience.failureThreshold must be between 1 and 10");
        }
        this.failureThreshold = failureThreshold;
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public void setCooldown(Duration cooldown) {
        if (cooldown == null
                || cooldown.isNegative()
                || cooldown.isZero()
                || cooldown.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException(
                    "discord-bot.playback-resilience.cooldown must be between 1ms and 30m");
        }
        this.cooldown = cooldown;
    }
}
