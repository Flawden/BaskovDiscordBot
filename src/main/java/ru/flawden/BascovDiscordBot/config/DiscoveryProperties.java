package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * Настройки внешнего discovery/recommendation слоя.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.discovery")
public class DiscoveryProperties {

    private String lastfmApiKey = "";
    private URI lastfmBaseUrl = URI.create("https://ws.audioscrobbler.com/2.0/");
    private Duration requestTimeout = Duration.ofSeconds(3);
    private int candidateLimit = 25;

    public String getLastfmApiKey() {
        return lastfmApiKey;
    }

    public void setLastfmApiKey(String lastfmApiKey) {
        this.lastfmApiKey = lastfmApiKey == null ? "" : lastfmApiKey.trim();
    }

    public URI getLastfmBaseUrl() {
        return lastfmBaseUrl;
    }

    public void setLastfmBaseUrl(URI lastfmBaseUrl) {
        if (lastfmBaseUrl == null || !"https".equalsIgnoreCase(lastfmBaseUrl.getScheme())) {
            throw new IllegalArgumentException("discord-bot.discovery.lastfmBaseUrl must use https");
        }
        this.lastfmBaseUrl = lastfmBaseUrl;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        if (requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()
                || requestTimeout.compareTo(Duration.ofSeconds(15)) > 0) {
            throw new IllegalArgumentException("discord-bot.discovery.requestTimeout must be between 1ms and 15s");
        }
        this.requestTimeout = requestTimeout;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        if (candidateLimit < 5 || candidateLimit > 100) {
            throw new IllegalArgumentException("discord-bot.discovery.candidateLimit must be between 5 and 100");
        }
        this.candidateLimit = candidateLimit;
    }

    public boolean lastfmEnabled() {
        return !lastfmApiKey.isBlank();
    }
}
