package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Настройки checkpoint/recovery активных музыкальных сессий.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.music-session")
public class MusicSessionProperties {

    private Path file = Path.of("data", "music-sessions.tsv");
    private Duration checkpointInterval = Duration.ofSeconds(5);
    private Duration maxAge = Duration.ofHours(6);
    private boolean restoreOnStartup = true;
    private boolean requireHumanListener = true;
    private boolean voiceRecoveryEnabled = true;
    private int maxRecoveryAttempts = 3;
    private Duration recoveryBackoff = Duration.ofSeconds(2);

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = Objects.requireNonNull(file, "discord-bot.music-session.file");
    }

    public Duration getCheckpointInterval() {
        return checkpointInterval;
    }

    public void setCheckpointInterval(Duration checkpointInterval) {
        if (checkpointInterval == null
                || checkpointInterval.isNegative()
                || checkpointInterval.isZero()
                || checkpointInterval.toMillis() < 1L
                || checkpointInterval.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException(
                    "discord-bot.music-session.checkpointInterval must be between 1ms and 1m");
        }
        this.checkpointInterval = checkpointInterval;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        if (maxAge == null
                || maxAge.isNegative()
                || maxAge.isZero()
                || maxAge.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException(
                    "discord-bot.music-session.maxAge must be between 1ms and 7d");
        }
        this.maxAge = maxAge;
    }

    public boolean isRestoreOnStartup() {
        return restoreOnStartup;
    }

    public void setRestoreOnStartup(boolean restoreOnStartup) {
        this.restoreOnStartup = restoreOnStartup;
    }

    public boolean isRequireHumanListener() {
        return requireHumanListener;
    }

    public void setRequireHumanListener(boolean requireHumanListener) {
        this.requireHumanListener = requireHumanListener;
    }

    public boolean isVoiceRecoveryEnabled() {
        return voiceRecoveryEnabled;
    }

    public void setVoiceRecoveryEnabled(boolean voiceRecoveryEnabled) {
        this.voiceRecoveryEnabled = voiceRecoveryEnabled;
    }

    public int getMaxRecoveryAttempts() {
        return maxRecoveryAttempts;
    }

    public void setMaxRecoveryAttempts(int maxRecoveryAttempts) {
        if (maxRecoveryAttempts < 1 || maxRecoveryAttempts > 10) {
            throw new IllegalArgumentException(
                    "discord-bot.music-session.maxRecoveryAttempts must be between 1 and 10");
        }
        this.maxRecoveryAttempts = maxRecoveryAttempts;
    }

    public Duration getRecoveryBackoff() {
        return recoveryBackoff;
    }

    public void setRecoveryBackoff(Duration recoveryBackoff) {
        if (recoveryBackoff == null
                || recoveryBackoff.isNegative()
                || recoveryBackoff.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException(
                    "discord-bot.music-session.recoveryBackoff must be between 0 and 1m");
        }
        this.recoveryBackoff = recoveryBackoff;
    }
}
