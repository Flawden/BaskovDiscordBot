package ru.flawden.BascovDiscordBot.product.api;

import java.time.LocalDate;
import java.util.List;

/** Versioned wire DTOs; internal product/domain records are never serialized directly. */
public final class ProductApiResponse {

    private ProductApiResponse() {
    }

    public record Capabilities(
            String apiVersion,
            String mode,
            boolean authenticationRequiredForReads,
            boolean mutationsEnabled,
            boolean authenticationRequiredForMutations,
            List<String> resources) {
    }

    public record Guilds(String userId, List<Guild> guilds) {
        public Guilds { guilds = List.copyOf(guilds == null ? List.of() : guilds); }
    }

    public record Guild(String guildId, String name) {
    }

    public record Home(
            String guildId,
            String userId,
            LocalDate date,
            Continuation continuation,
            List<Mix> today,
            List<Mix> forYou,
            List<Theme> themes,
            Library library,
            List<Track> recent,
            Taste taste) {
    }

    public record Mixes(
            String guildId,
            String userId,
            LocalDate date,
            Continuation continuation,
            List<Mix> today,
            List<Mix> forYou,
            List<Theme> themes) {
    }

    public record Player(
            String guildId,
            boolean sessionActive,
            boolean playing,
            boolean paused,
            int volume,
            String repeatMode,
            int queueSize,
            long positionMillis,
            long durationMillis,
            Track current,
            Radio radio) {
    }

    public record Library(
            String guildId,
            String userId,
            int favorites,
            int personalHistory,
            List<Track> recent,
            List<Track> favoriteTracks,
            List<Track> historyTracks) {
    }

    public record Search(
            String guildId,
            String userId,
            String query,
            List<Track> tracks) {
        public Search { tracks = List.copyOf(tracks == null ? List.of() : tracks); }
    }

    public record MixDetail(
            String guildId,
            String userId,
            String stationSlug,
            String label,
            String description,
            boolean available,
            boolean daily,
            List<Track> seedPreview) {
    }

    public record Continuation(
            String kind,
            String label,
            String stationSlug,
            String theme,
            LocalDate releaseDate,
            long generatedTracks) {
    }

    public record Mix(
            String stationSlug,
            String label,
            String description,
            boolean available,
            boolean daily) {
    }

    public record Theme(String name, double affinity) {
    }

    public record Track(String stableKey, String title, String artist) {
    }

    public record Taste(int evidenceSignals, double confidence, int recommendations) {
    }

    public record Radio(
            boolean enabled,
            String stationSlug,
            String theme,
            String strategy,
            long generatedTracks) {
    }

    public record Error(String code, String message) {
    }
}
