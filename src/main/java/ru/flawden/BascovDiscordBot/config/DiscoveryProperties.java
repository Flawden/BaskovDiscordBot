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
    private String listenbrainzToken = "";
    private URI listenbrainzBaseUrl = URI.create("https://api.listenbrainz.org");
    private int collaborativeArtistLimit = 12;
    private String listenbrainzRadioMode = "medium";

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

    public String getListenbrainzToken() {
        return listenbrainzToken;
    }

    public void setListenbrainzToken(String listenbrainzToken) {
        this.listenbrainzToken = listenbrainzToken == null ? "" : listenbrainzToken.trim();
    }

    public URI getListenbrainzBaseUrl() {
        return listenbrainzBaseUrl;
    }

    public void setListenbrainzBaseUrl(URI listenbrainzBaseUrl) {
        if (listenbrainzBaseUrl == null || !"https".equalsIgnoreCase(listenbrainzBaseUrl.getScheme())) {
            throw new IllegalArgumentException("discord-bot.discovery.listenbrainzBaseUrl must use https");
        }
        this.listenbrainzBaseUrl = listenbrainzBaseUrl;
    }

    public int getCollaborativeArtistLimit() {
        return collaborativeArtistLimit;
    }

    public void setCollaborativeArtistLimit(int collaborativeArtistLimit) {
        if (collaborativeArtistLimit < 3 || collaborativeArtistLimit > 50) {
            throw new IllegalArgumentException("discord-bot.discovery.collaborativeArtistLimit must be between 3 and 50");
        }
        this.collaborativeArtistLimit = collaborativeArtistLimit;
    }

    public String getListenbrainzRadioMode() {
        return listenbrainzRadioMode;
    }

    public void setListenbrainzRadioMode(String listenbrainzRadioMode) {
        String safe = listenbrainzRadioMode == null ? "medium" : listenbrainzRadioMode.trim().toLowerCase();
        if (!safe.equals("easy") && !safe.equals("medium") && !safe.equals("hard")) {
            throw new IllegalArgumentException("discord-bot.discovery.listenbrainzRadioMode must be easy, medium or hard");
        }
        this.listenbrainzRadioMode = safe;
    }

    public boolean listenbrainzEnabled() {
        return !listenbrainzToken.isBlank();
    }
}
