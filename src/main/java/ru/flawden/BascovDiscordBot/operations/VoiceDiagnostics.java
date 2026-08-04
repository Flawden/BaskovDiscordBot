package ru.flawden.BascovDiscordBot.operations;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит последнюю безопасную диагностику voice control/media transport.
 *
 * <p>Состояние переживает уничтожение музыкальной сессии в памяти процесса,
 * поэтому команда {@code /status} может объяснить, почему бот уже вышел из
 * голосового канала.</p>
 */
@Component
public class VoiceDiagnostics {

    private final Map<Long, State> states = new ConcurrentHashMap<>();
    private final Clock clock;
    private final String networkMode;
    private final MusicProperties properties;

    @Autowired
    public VoiceDiagnostics(Environment environment, MusicProperties properties) {
        this(
                environment.getProperty("discord-bot.runtime.network-mode", "bridge"),
                properties,
                Clock.systemUTC());
    }

    public VoiceDiagnostics(String networkMode, MusicProperties properties, Clock clock) {
        this.networkMode = sanitize(networkMode, "bridge");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void connectionRequested(long guildId, long channelId) {
        state(guildId).connectionRequested(channelId, clock.instant());
    }

    public void controlState(long guildId, long channelId, String controlState) {
        state(guildId).controlState(channelId, sanitize(controlState, "UNKNOWN"), clock.instant());
    }

    public void selfVoiceEvent(long guildId, String event, Long channelId) {
        state(guildId).selfVoiceEvent(sanitize(event, "UNKNOWN"), channelId, clock.instant());
    }

    public void voiceFailure(long guildId, String reason) {
        state(guildId).voiceFailure(sanitize(reason, "unknown voice failure"), clock.instant());
    }

    public void watchdogWarning(long guildId, String reason) {
        state(guildId).watchdogWarning(sanitize(reason, "voice watchdog warning"), clock.instant());
    }

    public void trackStarted(long guildId, String title) {
        state(guildId).trackStarted(sanitize(title, "unknown track"), clock.instant());
    }

    public void sourceFailure(long guildId, String title, String reason) {
        state(guildId).sourceFailure(
                sanitize(title, "unknown track"),
                sanitize(reason, "unknown source failure"),
                clock.instant());
    }

    public void cleanup(long guildId, String title) {
        state(guildId).cleanup(sanitize(title, "unknown track"), clock.instant());
    }

    public void fallback(long guildId, String fromTitle, String toTitle) {
        state(guildId).fallback(
                sanitize(fromTitle, "unknown track"),
                sanitize(toTitle, "unknown fallback"),
                clock.instant());
    }

    public void staleCallback(long guildId, String callback, String title) {
        state(guildId).staleCallback(
                sanitize(callback, "callback"),
                sanitize(title, "unknown track"),
                clock.instant());
    }

    public VoiceDiagnosticSnapshot snapshot(Guild guild, GuildMusicManager manager) {
        Objects.requireNonNull(guild, "guild");
        State state = states.computeIfAbsent(guild.getIdLong(), ignored -> new State());
        GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
        String channelId = voiceState != null && voiceState.getChannel() != null
                ? voiceState.getChannel().getId()
                : "none";
        boolean audioManagerConnected = guild.getAudioManager().isConnected();
        boolean sessionActive = manager != null && manager.isActive();
        boolean playbackExpected = sessionActive
                && (manager.getAudioPlayer().getPlayingTrack() != null
                || manager.getScheduler().getCurrentRequest() != null);
        String currentTrack = "none";
        long frameRequests = 0L;
        Duration frameAge = null;
        if (sessionActive) {
            TrackRequest current = manager.getScheduler().getCurrentRequest();
            if (current != null && current.track().getInfo() != null) {
                currentTrack = sanitize(current.track().getInfo().title, "unknown track");
            }
            frameRequests = manager.getSendHandler().frameRequestCount();
            frameAge = manager.getSendHandler().lastFrameRequestAge();
        }
        return state.snapshot(
                networkMode,
                channelId,
                audioManagerConnected,
                sessionActive,
                playbackExpected,
                currentTrack,
                frameRequests,
                frameAge,
                properties.isVoiceWatchdogEnforce());
    }

    private State state(long guildId) {
        return states.computeIfAbsent(guildId, ignored -> new State());
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String compact = value.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 177) + "...";
    }

    private static final class State {
        private String controlState = "IDLE";
        private long connectionAttempts;
        private long selfJoinEvents;
        private long selfLeaveEvents;
        private long trackExceptions;
        private long cleanupEvents;
        private long fallbackAttempts;
        private long staleCallbacks;
        private long watchdogWarnings;
        private String lastVoiceEvent = "none";
        private String lastVoiceError = "none";
        private String lastSourceError = "none";
        private String lastTrack = "none";

        synchronized void connectionRequested(long channelId, Instant now) {
            connectionAttempts++;
            controlState = "CONNECTING";
            lastVoiceEvent = timestamp(now) + " connection requested channel=" + channelId;
        }

        synchronized void controlState(long channelId, String state, Instant now) {
            controlState = state;
            lastVoiceEvent = timestamp(now) + " control=" + state + " channel=" + channelId;
        }

        synchronized void selfVoiceEvent(String event, Long channelId, Instant now) {
            if ("JOIN".equals(event)) {
                selfJoinEvents++;
            }
            if ("LEAVE".equals(event)) {
                selfLeaveEvents++;
            }
            controlState = switch (event) {
                case "JOIN", "MOVE" -> "SELF_" + event;
                case "LEAVE" -> "DISCONNECTED";
                default -> controlState;
            };
            lastVoiceEvent = timestamp(now) + " self=" + event + " channel="
                    + (channelId == null ? "none" : channelId);
        }

        synchronized void voiceFailure(String reason, Instant now) {
            controlState = "FAILED";
            lastVoiceError = timestamp(now) + " " + reason;
        }

        synchronized void watchdogWarning(String reason, Instant now) {
            watchdogWarnings++;
            lastVoiceError = timestamp(now) + " watchdog: " + reason;
        }

        synchronized void trackStarted(String title, Instant now) {
            lastTrack = timestamp(now) + " " + title;
        }

        synchronized void sourceFailure(String title, String reason, Instant now) {
            trackExceptions++;
            lastSourceError = timestamp(now) + " " + title + ": " + reason;
        }

        synchronized void cleanup(String title, Instant now) {
            cleanupEvents++;
            lastSourceError = timestamp(now) + " cleanup: " + title;
        }

        synchronized void fallback(String fromTitle, String toTitle, Instant now) {
            fallbackAttempts++;
            lastSourceError = timestamp(now) + " fallback: " + fromTitle + " -> " + toTitle;
        }

        synchronized void staleCallback(String callback, String title, Instant now) {
            staleCallbacks++;
            lastSourceError = timestamp(now) + " stale " + callback + ": " + title;
        }

        synchronized VoiceDiagnosticSnapshot snapshot(
                String networkMode,
                String channelId,
                boolean audioManagerConnected,
                boolean sessionActive,
                boolean playbackExpected,
                String currentTrack,
                long frameRequestCount,
                Duration frameAge,
                boolean watchdogEnforced) {
            return new VoiceDiagnosticSnapshot(
                    networkMode,
                    controlState,
                    channelId,
                    audioManagerConnected,
                    sessionActive,
                    playbackExpected,
                    "none".equals(currentTrack) ? lastTrack : currentTrack,
                    frameRequestCount,
                    frameAge,
                    connectionAttempts,
                    selfJoinEvents,
                    selfLeaveEvents,
                    trackExceptions,
                    cleanupEvents,
                    fallbackAttempts,
                    staleCallbacks,
                    watchdogWarnings,
                    lastVoiceEvent,
                    lastVoiceError,
                    lastSourceError,
                    watchdogEnforced);
        }

        private static String timestamp(Instant instant) {
            return instant == null ? "unknown" : instant.toString();
        }
    }
}
