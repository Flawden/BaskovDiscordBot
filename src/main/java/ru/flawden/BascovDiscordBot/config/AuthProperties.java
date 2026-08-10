package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Security and persistence settings for Baskov users and device sessions. */
@Component
@ConfigurationProperties(prefix = "baskov.auth")
public class AuthProperties {

    private Path file = Path.of("data", "baskov-auth.tsv");
    private Duration pairingTtl = Duration.ofMinutes(5);
    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private int maxDeviceSessions = 8;

    public Path getFile() { return file; }
    public void setFile(Path file) { this.file = Objects.requireNonNull(file, "baskov.auth.file"); }
    public Duration getPairingTtl() { return pairingTtl; }
    public void setPairingTtl(Duration pairingTtl) { this.pairingTtl = positive(pairingTtl, "pairingTtl"); }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = positive(accessTokenTtl, "accessTokenTtl"); }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = positive(refreshTokenTtl, "refreshTokenTtl"); }
    public int getMaxDeviceSessions() { return maxDeviceSessions; }
    public void setMaxDeviceSessions(int maxDeviceSessions) {
        if (maxDeviceSessions < 1 || maxDeviceSessions > 64) throw new IllegalArgumentException("maxDeviceSessions must be between 1 and 64");
        this.maxDeviceSessions = maxDeviceSessions;
    }
    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
