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

    private static final Pattern KEY_PATTERN = Pattern.compile("guild\\.(\\d+)\\.(volume|repeat)");
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
                    if ("volume".equals(matcher.group(2))) {
                        int volume = Integer.parseInt(value);
                        validateVolume(volume);
                        candidate.volume = volume;
                    } else {
                        candidate.repeatMode = RepeatMode.parse(value);
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
            GuildPreferences updated = new GuildPreferences(volume, current.repeatMode());
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
            GuildPreferences updated = new GuildPreferences(current.volume(), repeatMode);
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
        return new GuildPreferences(musicProperties.getDefaultVolume(), RepeatMode.OFF);
    }

    private void validateVolume(int volume) {
        if (volume < 0 || volume > musicProperties.getMaxVolume()) {
            throw new IllegalArgumentException(
                    "volume must be between 0 and " + musicProperties.getMaxVolume());
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
                    stored.setProperty(prefix + "volume", Integer.toString(entry.getValue().volume()));
                    stored.setProperty(prefix + "repeat", entry.getValue().repeatMode().name());
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

        private MutablePreferences(GuildPreferences defaults) {
            this.volume = defaults.volume();
            this.repeatMode = defaults.repeatMode();
        }

        private GuildPreferences toImmutable() {
            return new GuildPreferences(volume, repeatMode);
        }
    }
}
