package ru.flawden.BascovDiscordBot.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.DiscoveryProperties;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional ListenBrainz collaborative artist signal.
 *
 * Flow: metadata lookup (artist/title -> artist MBID) -> LB radio artist graph ->
 * normalized artist affinity. No recording is sent directly to playback.
 */
@Slf4j
@Component
public class ListenBrainzCollaborativeProvider implements CollaborativeSignalProvider, AutoCloseable {

    private static final int CACHE_LIMIT = 256;
    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(30).toMillis();

    private final DiscoveryProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public ListenBrainzCollaborativeProvider(DiscoveryProperties properties) {
        this(properties, new ObjectMapper());
    }

    ListenBrainzCollaborativeProvider(DiscoveryProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "listenbrainz-collaborative");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .executor(executor)
                .build();
    }

    @Override
    public String name() {
        return "ListenBrainz";
    }

    @Override
    public boolean available() {
        return properties.listenbrainzEnabled();
    }

    @Override
    public CompletableFuture<CollaborativeArtistSignals> signalsFor(StoredTrack seed) {
        if (!available() || seed == null || seed.author() == null || seed.author().isBlank()
                || seed.title() == null || seed.title().isBlank()) {
            return CompletableFuture.completedFuture(CollaborativeArtistSignals.empty());
        }
        String key = RecommendationIdentity.of(seed.author(), seed.title());
        CacheEntry cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.loadedAtEpochMillis() < CACHE_TTL_MILLIS) {
            return CompletableFuture.completedFuture(cached.signals());
        }
        return CompletableFuture.supplyAsync(() -> requestSignals(seed), executor)
                .exceptionally(exception -> {
                    log.warn("ListenBrainz collaborative request failed: {}", safeMessage(exception));
                    return CollaborativeArtistSignals.empty();
                });
    }

    private CollaborativeArtistSignals requestSignals(StoredTrack seed) {
        Optional<String> artistMbid = lookupArtistMbid(seed.author(), seed.title());
        if (artistMbid.isEmpty()) {
            return CollaborativeArtistSignals.empty();
        }
        CollaborativeArtistSignals signals = requestArtistRadio(artistMbid.get());
        if (signals.available()) {
            if (cache.size() >= CACHE_LIMIT) {
                evictOldest();
            }
            cache.put(RecommendationIdentity.of(seed.author(), seed.title()),
                    new CacheEntry(signals, System.currentTimeMillis()));
        }
        return signals;
    }

    private Optional<String> lookupArtistMbid(String artist, String title) {
        URI uri = buildLookupUri(artist, title);
        HttpRequest request = request(uri).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("ListenBrainz metadata lookup HTTP status={}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            JsonNode mbids = root.path("artist_mbids");
            if (!mbids.isArray() || mbids.isEmpty()) {
                return Optional.empty();
            }
            String value = mbids.get(0).asText("").trim();
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            log.debug("ListenBrainz metadata lookup failed: {}", safeMessage(exception));
            return Optional.empty();
        }
    }

    private CollaborativeArtistSignals requestArtistRadio(String artistMbid) {
        URI uri = buildArtistRadioUri(artistMbid);
        HttpRequest request = request(uri).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("ListenBrainz artist radio HTTP status={}", response.statusCode());
                return CollaborativeArtistSignals.empty();
            }
            return parseArtistSignals(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CollaborativeArtistSignals.empty();
        } catch (IOException | RuntimeException exception) {
            log.debug("ListenBrainz artist radio failed: {}", safeMessage(exception));
            return CollaborativeArtistSignals.empty();
        }
    }

    HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Token " + properties.getListenbrainzToken())
                .header("User-Agent", "BaskovDiscordBot/1.26.0 playback-resolver");
    }

    URI buildLookupUri(String artist, String title) {
        String base = trimSlash(properties.getListenbrainzBaseUrl().toString());
        return URI.create(base + "/1/metadata/lookup/?artist_name=" + encode(artist)
                + "&recording_name=" + encode(title));
    }

    URI buildArtistRadioUri(String artistMbid) {
        String base = trimSlash(properties.getListenbrainzBaseUrl().toString());
        return URI.create(base + "/1/lb-radio/artist/" + encodePath(artistMbid)
                + "?mode=" + properties.getListenbrainzRadioMode()
                + "&max_similar_artists=" + properties.getCollaborativeArtistLimit()
                + "&max_recordings_per_artist=1&pop_begin=5&pop_end=100");
    }

    CollaborativeArtistSignals parseArtistSignals(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
        ArrayList<ArtistCount> found = new ArrayList<>();
        collectArtistCounts(root, found);
        if (found.isEmpty()) {
            return CollaborativeArtistSignals.empty();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ArtistCount item : found) {
            counts.merge(item.artist(), Math.max(1L, item.listenCount()), Math::max);
        }
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        LinkedHashMap<String, Double> affinity = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(properties.getCollaborativeArtistLimit())
                .forEach(entry -> affinity.put(entry.getKey(), normalizedPopularity(entry.getValue(), max)));
        return new CollaborativeArtistSignals(name(), affinity);
    }

    private static void collectArtistCounts(JsonNode node, List<ArtistCount> target) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String artist = node.path("similar_artist_name").asText("").trim();
            if (!artist.isBlank()) {
                target.add(new ArtistCount(
                        RecommendationIdentity.normalizeArtist(artist),
                        Math.max(1L, node.path("total_listen_count").asLong(1L))));
            }
            node.elements().forEachRemaining(child -> collectArtistCounts(child, target));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectArtistCounts(child, target));
        }
    }

    private static double normalizedPopularity(long value, long max) {
        if (max <= 1L) {
            return 1.0d;
        }
        double numerator = Math.log1p(Math.max(1L, value));
        double denominator = Math.log1p(max);
        return Math.max(0.05d, Math.min(1.0d, numerator / denominator));
    }

    private void evictOldest() {
        cache.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().loadedAtEpochMillis()))
                .map(Map.Entry::getKey)
                .ifPresent(cache::remove);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String encodePath(String value) {
        return encode(value).replace("%2F", "");
    }

    private static String trimSlash(String value) {
        String safe = value == null ? "" : value.trim();
        while (safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    private static String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        String safe = message.trim().replaceAll("\\s+", " ");
        return safe.length() <= 180 ? safe : safe.substring(0, 180);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record ArtistCount(String artist, long listenCount) {
    }

    private record CacheEntry(CollaborativeArtistSignals signals, long loadedAtEpochMillis) {
    }
}
