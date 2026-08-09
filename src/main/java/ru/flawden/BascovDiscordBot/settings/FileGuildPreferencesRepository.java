package ru.flawden.BascovDiscordBot.settings;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.config.PersistenceProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Небольшое атомарно записываемое файловое хранилище без внешней БД.
 */
@Slf4j
@Repository
public class FileGuildPreferencesRepository implements GuildPreferencesRepository {

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "guild\\.(\\d+)\\.(volume|repeat|access|request-access|dj-role|manager-role|moderator-role|music-channel|vote-skip-percent|requester-queue-limit)");
    private static final Pattern AUDIT_KEY_PATTERN = Pattern.compile("guild\\.(\\d+)\\.audit\\.(\\d+)");
    private static final int MAX_AUDIT_ENTRIES = 25;
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Map<Long, GuildPreferences> preferences = new ConcurrentHashMap<>();
    private final Map<Long, Deque<GuildSettingsAuditEntry>> audit = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();
    private final Path file;
    private final MusicProperties musicProperties;

    public FileGuildPreferencesRepository(
            PersistenceProperties persistenceProperties,
            MusicProperties musicProperties) {
        this.file = persistenceProperties.getFile().toAbsolutePath().normalize();
        this.musicProperties = musicProperties;
    }

    @PostConstruct
    public void load() {
        synchronized (mutationLock) {
            preferences.clear();
            audit.clear();
            if (Files.notExists(file)) {
                log.info("Guild settings storage will be created on first change: {}", file);
                return;
            }

            Properties stored = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                stored.load(input);
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Cannot read guild settings from " + file, exception);
            }

            Map<Long, MutablePreferences> loaded = new HashMap<>();
            Map<Long, Map<Integer, GuildSettingsAuditEntry>> loadedAudit = new HashMap<>();
            for (String key : stored.stringPropertyNames()) {
                Matcher auditMatcher = AUDIT_KEY_PATTERN.matcher(key);
                if (auditMatcher.matches()) {
                    try {
                        long guildId = Long.parseLong(auditMatcher.group(1));
                        int index = Integer.parseInt(auditMatcher.group(2));
                        GuildSettingsAuditEntry entry = parseAudit(stored.getProperty(key, ""));
                        loadedAudit.computeIfAbsent(guildId, ignored -> new HashMap<>()).put(index, entry);
                    } catch (IllegalArgumentException exception) {
                        log.warn("Ignoring invalid guild settings audit {}: {}", key, exception.getMessage());
                    }
                    continue;
                }

                Matcher matcher = KEY_PATTERN.matcher(key);
                if (!matcher.matches()) {
                    log.warn("Ignoring unknown guild settings key: {}", key);
                    continue;
                }

                long guildId;
                try {
                    guildId = Long.parseLong(matcher.group(1));
                } catch (NumberFormatException exception) {
                    log.warn("Ignoring invalid guild id in settings key: {}", key);
                    continue;
                }

                MutablePreferences candidate = loaded.computeIfAbsent(
                        guildId,
                        ignored -> new MutablePreferences(defaults()));
                String value = stored.getProperty(key, "").trim();
                try {
                    switch (matcher.group(2)) {
                        case "volume" -> {
                            int volume = Integer.parseInt(value);
                            validateVolume(volume);
                            candidate.volume = volume;
                        }
                        case "repeat" -> candidate.repeatMode = RepeatMode.parse(value);
                        case "access" -> candidate.accessMode = PlaybackAccessMode.parse(value);
                        case "request-access" -> candidate.requestAccessMode = RequestAccessMode.parse(value);
                        case "dj-role" -> {
                            long roleId = Long.parseUnsignedLong(value);
                            if (roleId < 0) {
                                throw new IllegalArgumentException("role id cannot be negative");
                            }
                            candidate.djRoleId = roleId;
                        }
                        case "manager-role" -> {
                            long roleId = Long.parseUnsignedLong(value);
                            if (roleId < 0) {
                                throw new IllegalArgumentException("role id cannot be negative");
                            }
                            candidate.managerRoleId = roleId;
                        }
                        case "moderator-role" -> {
                            long roleId = Long.parseUnsignedLong(value);
                            if (roleId < 0) {
                                throw new IllegalArgumentException("role id cannot be negative");
                            }
                            candidate.moderatorRoleId = roleId;
                        }
                        case "music-channel" -> {
                            long channelId = Long.parseUnsignedLong(value);
                            if (channelId < 0) {
                                throw new IllegalArgumentException("channel id cannot be negative");
                            }
                            candidate.musicChannelId = channelId;
                        }
                        case "vote-skip-percent" -> {
                            int percent = Integer.parseInt(value);
                            validateVoteSkipPercent(percent);
                            candidate.voteSkipPercent = percent;
                        }
                        case "requester-queue-limit" -> {
                            int limit = Integer.parseInt(value);
                            validateRequesterQueueLimit(limit);
                            candidate.requesterQueueLimit = limit;
                        }
                        default -> throw new IllegalArgumentException("unknown key");
                    }
                } catch (IllegalArgumentException exception) {
                    log.warn("Ignoring invalid guild setting {}={}: {}", key, value, exception.getMessage());
                }
            }

            loaded.forEach((guildId, value) -> preferences.put(guildId, value.toImmutable()));
            loadedAudit.forEach((guildId, indexed) -> {
                Deque<GuildSettingsAuditEntry> entries = new ArrayDeque<>();
                indexed.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .limit(MAX_AUDIT_ENTRIES)
                        .map(Map.Entry::getValue)
                        .forEach(entries::addLast);
                if (!entries.isEmpty()) {
                    audit.put(guildId, entries);
                }
            });
            log.info("Loaded persistent preferences for {} Discord guilds from {}", preferences.size(), file);
        }
    }

    @Override
    public GuildPreferences get(long guildId) {
        validateGuildId(guildId);
        return preferences.getOrDefault(guildId, defaults());
    }

    @Override
    public GuildPreferences saveVolume(long guildId, int volume) {
        validateGuildId(guildId);
        validateVolume(volume);
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            GuildPreferences updated = new GuildPreferences(
                    volume,
                    current.repeatMode(),
                    current.accessMode(),
                    current.requestAccessMode(),
                    current.djRoleId(),
                    current.managerRoleId(),
                    current.moderatorRoleId(),
                    current.musicChannelId(),
                    current.voteSkipPercent(),
                    current.requesterQueueLimit());
            GuildPreferences previous = preferences.put(guildId, updated);
            try {
                persistLocked();
                return updated;
            } catch (RuntimeException exception) {
                restore(guildId, previous);
                throw exception;
            }
        }
    }

    @Override
    public GuildPreferences saveRepeatMode(long guildId, RepeatMode repeatMode) {
        validateGuildId(guildId);
        if (repeatMode == null) {
            throw new IllegalArgumentException("repeatMode cannot be null");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            GuildPreferences updated = new GuildPreferences(
                    current.volume(),
                    repeatMode,
                    current.accessMode(),
                    current.requestAccessMode(),
                    current.djRoleId(),
                    current.managerRoleId(),
                    current.moderatorRoleId(),
                    current.musicChannelId(),
                    current.voteSkipPercent(),
                    current.requesterQueueLimit());
            GuildPreferences previous = preferences.put(guildId, updated);
            try {
                persistLocked();
                return updated;
            } catch (RuntimeException exception) {
                restore(guildId, previous);
                throw exception;
            }
        }
    }

    @Override
    public GuildPreferences saveAccessMode(long guildId, PlaybackAccessMode accessMode) {
        validateGuildId(guildId);
        if (accessMode == null) {
            throw new IllegalArgumentException("accessMode cannot be null");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            GuildPreferences updated = new GuildPreferences(
                    current.volume(),
                    current.repeatMode(),
                    accessMode,
                    current.requestAccessMode(),
                    current.djRoleId(),
                    current.managerRoleId(),
                    current.moderatorRoleId(),
                    current.musicChannelId(),
                    current.voteSkipPercent(),
                    current.requesterQueueLimit());
            GuildPreferences previous = preferences.put(guildId, updated);
            try {
                persistLocked();
                return updated;
            } catch (RuntimeException exception) {
                restore(guildId, previous);
                throw exception;
            }
        }
    }

    @Override
    public GuildPreferences saveRequestAccessMode(long guildId, RequestAccessMode accessMode) {
        validateGuildId(guildId);
        if (accessMode == null) {
            throw new IllegalArgumentException("accessMode cannot be null");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            return replaceLocked(guildId, new GuildPreferences(
                    current.volume(), current.repeatMode(), current.accessMode(), accessMode,
                    current.djRoleId(), current.managerRoleId(), current.moderatorRoleId(),
                    current.musicChannelId(), current.voteSkipPercent(), current.requesterQueueLimit()));
        }
    }

    @Override
    public GuildPreferences saveDjRoleId(long guildId, long roleId) {
        validateGuildId(guildId);
        if (roleId < 0) {
            throw new IllegalArgumentException("roleId cannot be negative");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            GuildPreferences updated = new GuildPreferences(
                    current.volume(),
                    current.repeatMode(),
                    current.accessMode(),
                    current.requestAccessMode(),
                    roleId,
                    current.managerRoleId(),
                    current.moderatorRoleId(),
                    current.musicChannelId(),
                    current.voteSkipPercent(),
                    current.requesterQueueLimit());
            GuildPreferences previous = preferences.put(guildId, updated);
            try {
                persistLocked();
                return updated;
            } catch (RuntimeException exception) {
                restore(guildId, previous);
                throw exception;
            }
        }
    }

    @Override
    public GuildPreferences saveManagerRoleId(long guildId, long roleId) {
        validateGuildId(guildId);
        if (roleId < 0) {
            throw new IllegalArgumentException("roleId cannot be negative");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            return replaceLocked(guildId, new GuildPreferences(
                    current.volume(), current.repeatMode(), current.accessMode(), current.requestAccessMode(),
                    current.djRoleId(), roleId, current.moderatorRoleId(), current.musicChannelId(),
                    current.voteSkipPercent(), current.requesterQueueLimit()));
        }
    }

    @Override
    public GuildPreferences saveModeratorRoleId(long guildId, long roleId) {
        validateGuildId(guildId);
        if (roleId < 0) {
            throw new IllegalArgumentException("roleId cannot be negative");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            return replaceLocked(guildId, new GuildPreferences(
                    current.volume(), current.repeatMode(), current.accessMode(), current.requestAccessMode(),
                    current.djRoleId(), current.managerRoleId(), roleId, current.musicChannelId(),
                    current.voteSkipPercent(), current.requesterQueueLimit()));
        }
    }

    @Override
    public GuildPreferences saveRequesterQueueLimit(long guildId, int limit) {
        validateGuildId(guildId);
        validateRequesterQueueLimit(limit);
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            return replaceLocked(guildId, new GuildPreferences(
                    current.volume(), current.repeatMode(), current.accessMode(), current.requestAccessMode(),
                    current.djRoleId(), current.managerRoleId(), current.moderatorRoleId(), current.musicChannelId(),
                    current.voteSkipPercent(), limit));
        }
    }

    @Override
    public GuildPreferences saveMusicChannelId(long guildId, long channelId) {
        validateGuildId(guildId);
        if (channelId < 0) {
            throw new IllegalArgumentException("channelId cannot be negative");
        }
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            return replaceLocked(guildId, new GuildPreferences(
                    current.volume(), current.repeatMode(), current.accessMode(), current.requestAccessMode(),
                    current.djRoleId(), current.managerRoleId(), current.moderatorRoleId(), channelId,
                    current.voteSkipPercent(), current.requesterQueueLimit()));
        }
    }

    @Override
    public GuildPreferences saveVoteSkipPercent(long guildId, int percent) {
        validateGuildId(guildId);
        validateVoteSkipPercent(percent);
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            GuildPreferences updated = new GuildPreferences(
                    current.volume(),
                    current.repeatMode(),
                    current.accessMode(),
                    current.requestAccessMode(),
                    current.djRoleId(),
                    current.managerRoleId(),
                    current.moderatorRoleId(),
                    current.musicChannelId(),
                    percent,
                    current.requesterQueueLimit());
            GuildPreferences previous = preferences.put(guildId, updated);
            try {
                persistLocked();
                return updated;
            } catch (RuntimeException exception) {
                restore(guildId, previous);
                throw exception;
            }
        }
    }

    @Override
    public GuildPreferences replace(long guildId, GuildPreferences replacement) {
        validateGuildId(guildId);
        if (replacement == null) {
            throw new IllegalArgumentException("preferences cannot be null");
        }
        validateVolume(replacement.volume());
        validateVoteSkipPercent(replacement.voteSkipPercent());
        validateRequesterQueueLimit(replacement.requesterQueueLimit());
        synchronized (mutationLock) {
            return replaceLocked(guildId, replacement);
        }
    }

    @Override
    public void recordAudit(long guildId, long actorUserId, String action) {
        validateGuildId(guildId);
        GuildSettingsAuditEntry entry = new GuildSettingsAuditEntry(Instant.now(), actorUserId, action);
        synchronized (mutationLock) {
            Deque<GuildSettingsAuditEntry> previous = audit.get(guildId);
            Deque<GuildSettingsAuditEntry> updated = previous == null
                    ? new ArrayDeque<>()
                    : new ArrayDeque<>(previous);
            updated.addFirst(entry);
            while (updated.size() > MAX_AUDIT_ENTRIES) {
                updated.removeLast();
            }
            audit.put(guildId, updated);
            try {
                persistLocked();
            } catch (RuntimeException exception) {
                if (previous == null) {
                    audit.remove(guildId);
                } else {
                    audit.put(guildId, previous);
                }
                throw exception;
            }
        }
    }

    @Override
    public List<GuildSettingsAuditEntry> recentAudit(long guildId) {
        validateGuildId(guildId);
        synchronized (mutationLock) {
            Deque<GuildSettingsAuditEntry> entries = audit.get(guildId);
            return entries == null ? List.of() : List.copyOf(entries);
        }
    }

    @Override
    public GuildPreferences reset(long guildId) {
        validateGuildId(guildId);
        synchronized (mutationLock) {
            GuildPreferences previous = preferences.remove(guildId);
            try {
                persistLocked();
                return defaults();
            } catch (RuntimeException exception) {
                restore(guildId, previous);
                throw exception;
            }
        }
    }

    private GuildPreferences replaceLocked(long guildId, GuildPreferences updated) {
        GuildPreferences previous = preferences.put(guildId, updated);
        try {
            persistLocked();
            return updated;
        } catch (RuntimeException exception) {
            restore(guildId, previous);
            throw exception;
        }
    }

    private static GuildSettingsAuditEntry parseAudit(String raw) {
        String[] parts = raw.split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("audit entry must have timestamp|user|action");
        }
        return new GuildSettingsAuditEntry(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                Long.parseUnsignedLong(parts[1]),
                parts[2]);
    }

    private static String formatAudit(GuildSettingsAuditEntry entry) {
        return entry.occurredAt().toEpochMilli() + "|"
                + Long.toUnsignedString(entry.actorUserId()) + "|" + entry.action();
    }

    private void restore(long guildId, GuildPreferences previous) {
        if (previous == null) {
            preferences.remove(guildId);
        } else {
            preferences.put(guildId, previous);
        }
    }

    private GuildPreferences defaults() {
        return new GuildPreferences(
                musicProperties.getDefaultVolume(),
                RepeatMode.OFF,
                PlaybackAccessMode.OPEN,
                RequestAccessMode.OPEN,
                0L,
                0L,
                0L,
                0L,
                GuildPreferences.DEFAULT_VOTE_SKIP_PERCENT,
                0);
    }

    private void validateVolume(int volume) {
        if (volume < 0 || volume > musicProperties.getMaxVolume()) {
            throw new IllegalArgumentException(
                    "volume must be between 0 and " + musicProperties.getMaxVolume());
        }
    }

    private static void validateVoteSkipPercent(int percent) {
        if (percent < 25 || percent > 100) {
            throw new IllegalArgumentException("vote skip percent must be between 25 and 100");
        }
    }

    private static void validateRequesterQueueLimit(int limit) {
        if (limit < 0 || limit > 100) {
            throw new IllegalArgumentException("requester queue limit must be between 0 and 100");
        }
    }

    private static void validateGuildId(long guildId) {
        if (guildId <= 0) {
            throw new IllegalArgumentException("guildId must be positive");
        }
    }

    private void persistLocked() {
        Properties stored = new Properties();
        preferences.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String prefix = "guild." + Long.toUnsignedString(entry.getKey()) + ".";
                    GuildPreferences value = entry.getValue();
                    stored.setProperty(prefix + "volume", Integer.toString(value.volume()));
                    stored.setProperty(prefix + "repeat", value.repeatMode().name());
                    stored.setProperty(prefix + "access", value.accessMode().name());
                    stored.setProperty(prefix + "request-access", value.requestAccessMode().name());
                    stored.setProperty(prefix + "dj-role", Long.toUnsignedString(value.djRoleId()));
                    stored.setProperty(prefix + "manager-role", Long.toUnsignedString(value.managerRoleId()));
                    stored.setProperty(prefix + "moderator-role", Long.toUnsignedString(value.moderatorRoleId()));
                    stored.setProperty(prefix + "music-channel", Long.toUnsignedString(value.musicChannelId()));
                    stored.setProperty(prefix + "vote-skip-percent", Integer.toString(value.voteSkipPercent()));
                    stored.setProperty(prefix + "requester-queue-limit", Integer.toString(value.requesterQueueLimit()));
                });
        audit.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String prefix = "guild." + Long.toUnsignedString(entry.getKey()) + ".audit.";
                    int index = 0;
                    for (GuildSettingsAuditEntry auditEntry : entry.getValue()) {
                        stored.setProperty(prefix + index++, formatAudit(auditEntry));
                    }
                });

        Path parent = file.getParent();
        if (parent == null) {
            throw new IllegalStateException("Guild settings file has no parent directory: " + file);
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                stored.store(output, "Baskov Discord Bot guild settings");
            }
            setOwnerOnlyPermissions(temporary);
            moveAtomically(temporary, file);
            setOwnerOnlyPermissions(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist guild settings to " + file, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.warn("Cannot delete temporary guild settings file {}", temporary, exception);
                }
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setOwnerOnlyPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException exception) {
            // Windows and some filesystems do not support POSIX permissions.
        }
    }

    private static final class MutablePreferences {
        private int volume;
        private RepeatMode repeatMode;
        private PlaybackAccessMode accessMode;
        private RequestAccessMode requestAccessMode;
        private long djRoleId;
        private long managerRoleId;
        private long moderatorRoleId;
        private long musicChannelId;
        private int voteSkipPercent;
        private int requesterQueueLimit;

        private MutablePreferences(GuildPreferences defaults) {
            this.volume = defaults.volume();
            this.repeatMode = defaults.repeatMode();
            this.accessMode = defaults.accessMode();
            this.requestAccessMode = defaults.requestAccessMode();
            this.djRoleId = defaults.djRoleId();
            this.managerRoleId = defaults.managerRoleId();
            this.moderatorRoleId = defaults.moderatorRoleId();
            this.musicChannelId = defaults.musicChannelId();
            this.voteSkipPercent = defaults.voteSkipPercent();
            this.requesterQueueLimit = defaults.requesterQueueLimit();
        }

        private GuildPreferences toImmutable() {
            return new GuildPreferences(
                    volume, repeatMode, accessMode, requestAccessMode,
                    djRoleId, managerRoleId, moderatorRoleId, musicChannelId, voteSkipPercent, requesterQueueLimit);
        }
    }
}
