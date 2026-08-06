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
import java.util.EnumSet;
import java.util.HashMap;
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
            "guild\\.(\\d+)\\.(volume|repeat|access|dj-role|vote-skip-percent)");
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Map<Long, GuildPreferences> preferences = new ConcurrentHashMap<>();
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
            for (String key : stored.stringPropertyNames()) {
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
                        case "dj-role" -> {
                            long roleId = Long.parseUnsignedLong(value);
                            if (roleId < 0) {
                                throw new IllegalArgumentException("role id cannot be negative");
                            }
                            candidate.djRoleId = roleId;
                        }
                        case "vote-skip-percent" -> {
                            int percent = Integer.parseInt(value);
                            validateVoteSkipPercent(percent);
                            candidate.voteSkipPercent = percent;
                        }
                        default -> throw new IllegalArgumentException("unknown key");
                    }
                } catch (IllegalArgumentException exception) {
                    log.warn("Ignoring invalid guild setting {}={}: {}", key, value, exception.getMessage());
                }
            }

            loaded.forEach((guildId, value) -> preferences.put(guildId, value.toImmutable()));
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
                    current.djRoleId(),
                    current.voteSkipPercent());
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
                    current.djRoleId(),
                    current.voteSkipPercent());
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
                    current.djRoleId(),
                    current.voteSkipPercent());
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
                    roleId,
                    current.voteSkipPercent());
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
    public GuildPreferences saveVoteSkipPercent(long guildId, int percent) {
        validateGuildId(guildId);
        validateVoteSkipPercent(percent);
        synchronized (mutationLock) {
            GuildPreferences current = preferences.getOrDefault(guildId, defaults());
            GuildPreferences updated = new GuildPreferences(
                    current.volume(),
                    current.repeatMode(),
                    current.accessMode(),
                    current.djRoleId(),
                    percent);
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
                0L,
                GuildPreferences.DEFAULT_VOTE_SKIP_PERCENT);
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
                    stored.setProperty(prefix + "dj-role", Long.toUnsignedString(value.djRoleId()));
                    stored.setProperty(prefix + "vote-skip-percent", Integer.toString(value.voteSkipPercent()));
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
        private long djRoleId;
        private int voteSkipPercent;

        private MutablePreferences(GuildPreferences defaults) {
            this.volume = defaults.volume();
            this.repeatMode = defaults.repeatMode();
            this.accessMode = defaults.accessMode();
            this.djRoleId = defaults.djRoleId();
            this.voteSkipPercent = defaults.voteSkipPercent();
        }

        private GuildPreferences toImmutable() {
            return new GuildPreferences(volume, repeatMode, accessMode, djRoleId, voteSkipPercent);
        }
    }
}
