package ru.flawden.BascovDiscordBot.recommendation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.flawden.BascovDiscordBot.config.RecommendationFeedbackProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Atomic, bounded TSV journal of recommendation outcomes.
 */
@Slf4j
@Repository
public class FileRecommendationFeedbackRepository implements RecommendationFeedbackRepository {

    private static final String HEADER = "BASKOV_RECOMMENDATION_FEEDBACK_V2";
    private static final String LEGACY_HEADER = "BASKOV_RECOMMENDATION_FEEDBACK_V1";
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Path file;
    private final Object mutationLock = new Object();
    private final Map<Long, Map<Long, List<RecommendationFeedbackEntry>>> entries = new LinkedHashMap<>();

    public FileRecommendationFeedbackRepository(RecommendationFeedbackProperties properties) {
        this.file = properties.getFile().toAbsolutePath().normalize();
    }

    @PostConstruct
    public void load() {
        synchronized (mutationLock) {
            entries.clear();
            if (Files.notExists(file)) {
                log.info("Recommendation feedback storage will be created on first recommendation: {}", file);
                return;
            }
            final List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read recommendation feedback from " + file, exception);
            }
            if (lines.isEmpty()) {
                return;
            }
            boolean legacyV1 = LEGACY_HEADER.equals(lines.get(0));
            if (!HEADER.equals(lines.get(0)) && !legacyV1) {
                throw new IllegalStateException("Unsupported recommendation feedback format in " + file);
            }
            for (int index = 1; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                try {
                    loadLine(line, legacyV1);
                } catch (RuntimeException exception) {
                    log.warn("Ignoring malformed recommendation feedback line {}: {}", index + 1, exception.getMessage());
                }
            }
            trimAll();
            int total = entries.values().stream()
                    .flatMap(map -> map.values().stream())
                    .mapToInt(List::size)
                    .sum();
            log.info("Loaded {} recommendation feedback entries from {}", total, file);
        }
    }

    @Override
    public RecommendationFeedbackEntry recordRecommendation(RecommendationFeedbackEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry cannot be null");
        }
        synchronized (mutationLock) {
            Map<Long, List<RecommendationFeedbackEntry>> guild = entries.computeIfAbsent(
                    entry.guildId(), ignored -> new LinkedHashMap<>());
            ArrayList<RecommendationFeedbackEntry> user = new ArrayList<>(guild.getOrDefault(entry.userId(), List.of()));
            RecommendationFeedbackEntry normalized = entry;
            user.add(0, normalized);
            while (user.size() > MAX_ENTRIES_PER_USER) {
                user.remove(user.size() - 1);
            }
            guild.put(entry.userId(), List.copyOf(user));
            persistLocked();
            return normalized;
        }
    }

    @Override
    public Optional<RecommendationFeedbackEntry> recordLatestOutcome(
            long guildId,
            String trackIdentity,
            RecommendationOutcome outcome,
            double completionRatio) {
        validateGuild(guildId);
        String identity = normalizeIdentity(trackIdentity);
        synchronized (mutationLock) {
            Map<Long, List<RecommendationFeedbackEntry>> guild = entries.get(guildId);
            if (guild == null || guild.isEmpty()) {
                return Optional.empty();
            }
            long selectedUser = 0L;
            int selectedIndex = -1;
            long latest = Long.MIN_VALUE;
            for (Map.Entry<Long, List<RecommendationFeedbackEntry>> userEntry : guild.entrySet()) {
                List<RecommendationFeedbackEntry> history = userEntry.getValue();
                for (int index = 0; index < history.size(); index++) {
                    RecommendationFeedbackEntry candidate = history.get(index);
                    if (candidate.trackIdentity().equals(identity)
                            && candidate.recommendedAtEpochMillis() > latest) {
                        latest = candidate.recommendedAtEpochMillis();
                        selectedUser = userEntry.getKey();
                        selectedIndex = index;
                    }
                }
            }
            if (selectedIndex < 0) {
                return Optional.empty();
            }
            return updateOutcome(guild, selectedUser, selectedIndex, outcome, completionRatio);
        }
    }

    @Override
    public Optional<RecommendationFeedbackEntry> recordUserOutcome(
            long guildId,
            long userId,
            String trackIdentity,
            RecommendationOutcome outcome,
            double completionRatio) {
        validateGuild(guildId);
        validateUser(userId);
        String identity = normalizeIdentity(trackIdentity);
        synchronized (mutationLock) {
            Map<Long, List<RecommendationFeedbackEntry>> guild = entries.get(guildId);
            if (guild == null) {
                return Optional.empty();
            }
            List<RecommendationFeedbackEntry> history = guild.getOrDefault(userId, List.of());
            for (int index = 0; index < history.size(); index++) {
                if (history.get(index).trackIdentity().equals(identity)) {
                    return updateOutcome(guild, userId, index, outcome, completionRatio);
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public List<RecommendationFeedbackEntry> history(long guildId, long userId, int limit) {
        validateGuild(guildId);
        validateUser(userId);
        int safeLimit = Math.max(1, Math.min(MAX_ENTRIES_PER_USER, limit));
        synchronized (mutationLock) {
            List<RecommendationFeedbackEntry> history = entries
                    .getOrDefault(guildId, Map.of())
                    .getOrDefault(userId, List.of());
            return List.copyOf(history.subList(0, Math.min(safeLimit, history.size())));
        }
    }

    private Optional<RecommendationFeedbackEntry> updateOutcome(
            Map<Long, List<RecommendationFeedbackEntry>> guild,
            long userId,
            int index,
            RecommendationOutcome outcome,
            double completionRatio) {
        List<RecommendationFeedbackEntry> current = guild.getOrDefault(userId, List.of());
        if (index < 0 || index >= current.size()) {
            return Optional.empty();
        }
        RecommendationFeedbackEntry updated = current.get(index).withOutcome(
                outcome,
                System.currentTimeMillis(),
                completionRatio);
        ArrayList<RecommendationFeedbackEntry> mutable = new ArrayList<>(current);
        mutable.set(index, updated);
        guild.put(userId, List.copyOf(mutable));
        persistLocked();
        return Optional.of(updated);
    }

    private void loadLine(String line, boolean legacyV1) {
        String[] columns = line.split("\t", -1);
        int expected = legacyV1 ? 19 : 20;
        if (columns.length != expected || !"R".equals(columns[0])) {
            throw new IllegalArgumentException("expected R record with " + expected + " columns");
        }
        int offset = legacyV1 ? 0 : 1;
        Set<String> tags = legacyV1 ? Set.of() : decodeTags(columns[9]);
        RecommendationFeedbackEntry entry = new RecommendationFeedbackEntry(
                decode(columns[1]),
                positiveLong(columns[2], "guildId"),
                positiveLong(columns[3], "userId"),
                decode(columns[4]),
                decode(columns[5]),
                decode(columns[6]),
                decode(columns[7]),
                decode(columns[8]),
                tags,
                RadioStrategy.valueOf(columns[9 + offset]),
                decode(columns[10 + offset]),
                Double.parseDouble(columns[11 + offset]),
                positiveLong(columns[12 + offset], "recommendedAt"),
                RecommendationOutcome.valueOf(columns[13 + offset]),
                nonNegativeLong(columns[14 + offset], "lastOutcomeAt"),
                nonNegativeInt(columns[15 + offset], "positiveSignals"),
                nonNegativeInt(columns[16 + offset], "negativeSignals"),
                Double.parseDouble(columns[17 + offset]),
                Double.parseDouble(columns[18 + offset]));
        Map<Long, List<RecommendationFeedbackEntry>> guild = entries.computeIfAbsent(
                entry.guildId(), ignored -> new LinkedHashMap<>());
        ArrayList<RecommendationFeedbackEntry> user = new ArrayList<>(guild.getOrDefault(entry.userId(), List.of()));
        user.add(entry);
        guild.put(entry.userId(), List.copyOf(user));
    }

    private void trimAll() {
        entries.forEach((guildId, guild) -> guild.replaceAll((userId, history) -> history.stream()
                .sorted(Comparator.comparingLong(RecommendationFeedbackEntry::recommendedAtEpochMillis).reversed())
                .limit(MAX_ENTRIES_PER_USER)
                .toList()));
    }

    private void persistLocked() {
        Path parent = file.getParent();
        if (parent == null) {
            throw new IllegalStateException("Recommendation feedback file has no parent directory: " + file);
        }
        ArrayList<String> lines = new ArrayList<>();
        lines.add(HEADER);
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(guildEntry -> guildEntry.getValue().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(userEntry -> userEntry.getValue().forEach(entry -> lines.add(encodeEntry(entry)))));

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            setOwnerOnlyPermissions(temporary);
            moveAtomically(temporary, file);
            setOwnerOnlyPermissions(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist recommendation feedback to " + file, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.warn("Cannot delete temporary recommendation feedback file {}", temporary, exception);
                }
            }
        }
    }

    private static String encodeEntry(RecommendationFeedbackEntry entry) {
        return String.join("\t",
                "R",
                encode(entry.id()),
                Long.toString(entry.guildId()),
                Long.toString(entry.userId()),
                encode(entry.seedArtist()),
                encode(entry.seedTitle()),
                encode(entry.trackArtist()),
                encode(entry.trackTitle()),
                encode(entry.trackIdentity()),
                encodeTags(entry.tags()),
                entry.strategy().name(),
                encode(entry.provider()),
                Double.toString(entry.similarity()),
                Long.toString(entry.recommendedAtEpochMillis()),
                entry.lastOutcome().name(),
                Long.toString(entry.lastOutcomeAtEpochMillis()),
                Integer.toString(entry.positiveSignals()),
                Integer.toString(entry.negativeSignals()),
                Double.toString(entry.signalScore()),
                Double.toString(entry.lastCompletionRatio()));
    }

    public static RecommendationFeedbackEntry pending(
            long guildId,
            long userId,
            String seedArtist,
            String seedTitle,
            String trackArtist,
            String trackTitle,
            String trackIdentity,
            Set<String> tags,
            RadioStrategy strategy,
            String provider,
            double similarity) {
        return new RecommendationFeedbackEntry(
                UUID.randomUUID().toString(),
                guildId,
                userId,
                seedArtist,
                seedTitle,
                trackArtist,
                trackTitle,
                normalizeIdentity(trackIdentity),
                tags,
                strategy,
                provider,
                similarity,
                System.currentTimeMillis(),
                RecommendationOutcome.PENDING,
                0L,
                0,
                0,
                0.0d,
                0.0d);
    }

    public static RecommendationFeedbackEntry pending(
            long guildId,
            long userId,
            String seedArtist,
            String seedTitle,
            String trackArtist,
            String trackTitle,
            String trackIdentity,
            RadioStrategy strategy,
            String provider,
            double similarity) {
        return pending(guildId, userId, seedArtist, seedTitle, trackArtist, trackTitle,
                trackIdentity, Set.of(), strategy, provider, similarity);
    }

    private static String encodeTags(Set<String> tags) {
        return encode(tags == null || tags.isEmpty() ? "" : String.join("\u001f", tags));
    }

    private static Set<String> decodeTags(String value) {
        String decoded = decode(value);
        if (decoded.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(decoded.split("\u001f"))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .limit(8)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String normalizeIdentity(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void validateGuild(long guildId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
    }

    private static void validateUser(long userId) {
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static long positiveLong(String value, String field) {
        long parsed = Long.parseLong(value);
        if (parsed <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return parsed;
    }

    private static long nonNegativeLong(String value, String field) {
        long parsed = Long.parseLong(value);
        if (parsed < 0L) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
        return parsed;
    }

    private static int nonNegativeInt(String value, String field) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
        return parsed;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setOwnerOnlyPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException exception) {
            // Windows and some filesystems do not expose POSIX permissions.
        }
    }
}
