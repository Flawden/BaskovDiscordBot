package ru.flawden.BascovDiscordBot.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Last.fm candidate generator. Не участвует в playback: возвращает только artist/title.
 */
@Slf4j
@Component
public class LastFmRecommendationProvider implements RecommendationProvider {

    private final DiscoveryProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private static final long TAG_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int TAG_ENRICH_LIMIT = 5;
    private static final int TAG_LIMIT = 5;

    private final HttpClient httpClient;
    private final ExecutorService metadataExecutor;
    private final Map<String, TagCacheEntry> tagCache = new ConcurrentHashMap<>();

    @Autowired
    public LastFmRecommendationProvider(DiscoveryProperties properties) {
        this(properties, new ObjectMapper());
    }

    LastFmRecommendationProvider(DiscoveryProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "baskov-lastfm-discovery");
            thread.setDaemon(true);
            return thread;
        });
        this.metadataExecutor = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "baskov-lastfm-tags");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        log.info("Smart discovery provider initialized: provider=Last.fm enabled={} timeout={} candidateLimit={}",
                available(), properties.getRequestTimeout(), properties.getCandidateLimit());
    }

    @Override
    public String name() {
        return "Last.fm";
    }

    @Override
    public boolean available() {
        return properties.lastfmEnabled();
    }

    @Override
    public CompletableFuture<List<RecommendationCandidate>> similarTracks(StoredTrack seed, int limit) {
        if (!available() || seed == null || seed.title() == null || seed.title().isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        int boundedLimit = Math.max(1, Math.min(limit, properties.getCandidateLimit()));
        return CompletableFuture.supplyAsync(() -> requestSimilar(seed, boundedLimit), executor)
                .thenCompose(this::enrichTopCandidates)
                .exceptionally(exception -> {
                    log.warn("Last.fm recommendation request failed: {}", safeMessage(exception));
                    return List.of();
                });
    }

    private List<RecommendationCandidate> requestSimilar(StoredTrack seed, int limit) {
        String artist = seed.author() == null ? "" : seed.author().trim();
        String track = seed.title().trim();
        if (artist.isBlank() || "Неизвестно".equalsIgnoreCase(artist)) {
            return List.of();
        }

        URI uri = buildUri("track.getsimilar", artist, track, limit);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", "BaskovDiscordBot/1.24.0 personalized-home")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Last.fm recommendation HTTP status={}", response.statusCode());
                return List.of();
            }
            return parseSimilarTracks(response.body(), limit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException | RuntimeException exception) {
            log.warn("Last.fm recommendation response failed: {}", safeMessage(exception));
            return List.of();
        }
    }

    URI buildUri(String method, String artist, String track, int limit) {
        String base = properties.getLastfmBaseUrl().toString();
        String separator = base.contains("?") ? "&" : "?";
        String query = "method=" + encode(method)
                + "&artist=" + encode(artist)
                + "&track=" + encode(track)
                + "&autocorrect=1"
                + "&limit=" + limit
                + "&api_key=" + encode(properties.getLastfmApiKey())
                + "&format=json";
        return URI.create(base + separator + query);
    }

    URI buildTopTagsUri(String artist, String track) {
        String base = properties.getLastfmBaseUrl().toString();
        String separator = base.contains("?") ? "&" : "?";
        String query = "method=track.gettoptags"
                + "&artist=" + encode(artist)
                + "&track=" + encode(track)
                + "&autocorrect=1"
                + "&api_key=" + encode(properties.getLastfmApiKey())
                + "&format=json";
        return URI.create(base + separator + query);
    }

    List<RecommendationCandidate> parseSimilarTracks(String json, int limit) throws IOException {
        JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
        JsonNode tracks = root.path("similartracks").path("track");
        if (!tracks.isArray()) {
            return List.of();
        }
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (JsonNode node : tracks) {
            if (candidates.size() >= limit) {
                break;
            }
            String title = node.path("name").asText("").trim();
            String artist = node.path("artist").path("name").asText("").trim();
            if (artist.isBlank()) {
                artist = node.path("artist").asText("").trim();
            }
            if (title.isBlank() || artist.isBlank()) {
                continue;
            }
            double similarity = parseMatch(node.path("match").asText("0"));
            String reason = "Last.fm similarity " + Math.round(similarity * 100.0d) + "% к seed `"
                    + artistSafe(artist) + " — " + titleSafe(title) + "`";
            candidates.add(new RecommendationCandidate(
                    artist,
                    title,
                    similarity,
                    name(),
                    reason,
                    cachedTags(artist, title)));
        }
        return List.copyOf(candidates);
    }


    @Override
    public CompletableFuture<RecommendationCandidate> enrich(RecommendationCandidate candidate) {
        if (candidate == null || !available()) {
            return CompletableFuture.completedFuture(candidate);
        }
        Set<String> cached = cachedTags(candidate.artist(), candidate.title());
        if (!cached.isEmpty()) {
            return CompletableFuture.completedFuture(candidate.withTags(cached));
        }
        return CompletableFuture.supplyAsync(() -> {
            Set<String> tags = requestTopTags(candidate.artist(), candidate.title());
            if (!tags.isEmpty()) {
                tagCache.put(tagKey(candidate.artist(), candidate.title()),
                        new TagCacheEntry(tags, System.currentTimeMillis()));
                return candidate.withTags(tags);
            }
            return candidate;
        }, metadataExecutor).exceptionally(exception -> candidate);
    }

    private CompletableFuture<List<RecommendationCandidate>> enrichTopCandidates(
            List<RecommendationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty() || !available()) {
            return CompletableFuture.completedFuture(candidates == null ? List.of() : candidates);
        }
        int count = Math.min(TAG_ENRICH_LIMIT, candidates.size());
        List<CompletableFuture<RecommendationCandidate>> futures = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            futures.add(enrich(candidates.get(index)));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    ArrayList<RecommendationCandidate> enriched = new ArrayList<>(candidates);
                    for (int index = 0; index < futures.size(); index++) {
                        enriched.set(index, futures.get(index).join());
                    }
                    return List.copyOf(enriched);
                });
    }

    private Set<String> cachedTags(String artist, String title) {
        TagCacheEntry entry = tagCache.get(tagKey(artist, title));
        if (entry == null || System.currentTimeMillis() - entry.loadedAtEpochMillis() >= TAG_CACHE_TTL_MILLIS) {
            return Set.of();
        }
        return entry.tags();
    }

    private Set<String> requestTopTags(String artist, String title) {
        try {
            URI uri = buildTopTagsUri(artist, title);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("User-Agent", "BaskovDiscordBot/1.24.0 personalized-home")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Set.of();
            }
            JsonNode nodes = objectMapper.readTree(response.body()).path("toptags").path("tag");
            if (!nodes.isArray()) {
                return Set.of();
            }
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            for (JsonNode node : nodes) {
                String name = node.path("name").asText("").trim().toLowerCase(Locale.ROOT);
                if (!name.isBlank()) {
                    tags.add(name);
                }
                if (tags.size() >= TAG_LIMIT) {
                    break;
                }
            }
            return Set.copyOf(tags);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Set.of();
        } catch (IOException | RuntimeException exception) {
            log.debug("Last.fm tag enrichment failed for {} - {}: {}", artist, title, safeMessage(exception));
            return Set.of();
        }
    }

    private static String tagKey(String artist, String title) {
        return RecommendationIdentity.of(artist, title);
    }

    private record TagCacheEntry(Set<String> tags, long loadedAtEpochMillis) {
        private TagCacheEntry {
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }
    }

    private static double parseMatch(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String artistSafe(String artist) {
        return artist.replace('`', '’');
    }

    private static String titleSafe(String title) {
        return title.replace('`', '’');
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        metadataExecutor.shutdownNow();
    }
}
