package ru.flawden.BascovDiscordBot.dave;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Безопасное runtime-состояние native libDAVE без криптографических данных.
 */
@Component
public final class DaveRuntimeInfo {

    public static final String IMPLEMENTATION = "libdave-jvm";
    public static final String IMPLEMENTATION_VERSION = "0.1.3";

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
            new Snapshot("NOT_LOADED", IMPLEMENTATION, IMPLEMENTATION_VERSION,
                    0, platform(), "none"));

    public void ready(int maxProtocolVersion) {
        if (maxProtocolVersion <= 0) {
            throw new IllegalArgumentException("DAVE maximum protocol version must be positive");
        }
        snapshot.set(new Snapshot(
                "READY",
                IMPLEMENTATION,
                IMPLEMENTATION_VERSION,
                maxProtocolVersion,
                platform(),
                "none"));
    }

    public void failed(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        snapshot.set(new Snapshot(
                "FAILED",
                IMPLEMENTATION,
                IMPLEMENTATION_VERSION,
                0,
                platform(),
                sanitize(failure)));
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    private static String platform() {
        String os = System.getProperty("os.name", "unknown")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-");
        String arch = System.getProperty("os.arch", "unknown")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-");
        return os + "-" + arch;
    }

    private static String sanitize(Throwable failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= 180 ? value : value.substring(0, 177) + "...";
    }

    public record Snapshot(
            String status,
            String implementation,
            String implementationVersion,
            int maxProtocolVersion,
            String platform,
            String error) {

        public boolean ready() {
            return "READY".equals(status) && maxProtocolVersion > 0;
        }
    }
}
