package ru.flawden.BascovDiscordBot.session;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.MusicSessionProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Атомарное TSV-хранилище checkpoint активных музыкальных сессий.
 */
@Slf4j
@Repository
public class FileMusicSessionRepository implements MusicSessionRepository {

    static final String HEADER = "BASKOV_MUSIC_SESSIONS_V2";
    static final String LEGACY_HEADER_V1 = "BASKOV_MUSIC_SESSIONS_V1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Map<Long, StoredMusicSession> sessions = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();
    private final Path file;

    public FileMusicSessionRepository(MusicSessionProperties properties) {
        this.file = properties.getFile().toAbsolutePath().normalize();
    }

    @PostConstruct
    public void load() {
        synchronized (mutationLock) {
            sessions.clear();
            if (Files.notExists(file)) {
                log.info("Music session checkpoint storage will be created on first active session: {}", file);
                return;
            }
            final List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read music session checkpoints from " + file, exception);
            }
            if (lines.isEmpty()) {
                log.warn("Music session checkpoint file is empty: {}", file);
                return;
            }
            String header = lines.get(0);
            if (!HEADER.equals(header) && !LEGACY_HEADER_V1.equals(header)) {
                throw new IllegalStateException("Unsupported music session checkpoint format in " + file);
            }
            boolean legacyV1 = LEGACY_HEADER_V1.equals(header);
            for (int index = 1; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                try {
                    StoredMusicSession session = decodeSession(line, legacyV1);
                    sessions.put(session.guildId(), session);
                } catch (RuntimeException exception) {
                    log.warn("Ignoring malformed music session checkpoint line {}: {}",
                            index + 1,
                            exception.getMessage());
                }
            }
            log.info("Loaded {} restorable music sessions from {}", sessions.size(), file);
        }
    }

    @Override
    public List<StoredMusicSession> sessions() {
        return sessions.values().stream()
                .sorted(Comparator.comparingLong(StoredMusicSession::guildId))
                .toList();
    }

    @Override
    public Optional<StoredMusicSession> session(long guildId) {
        validateGuildId(guildId);
        return Optional.ofNullable(sessions.get(guildId));
    }

    @Override
    public void save(StoredMusicSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        synchronized (mutationLock) {
            StoredMusicSession previous = sessions.put(session.guildId(), session);
            try {
                persist();
            } catch (RuntimeException exception) {
                if (previous == null) {
                    sessions.remove(session.guildId(), session);
                } else {
                    sessions.put(session.guildId(), previous);
                }
                throw exception;
            }
        }
    }

    @Override
    public void remove(long guildId) {
        validateGuildId(guildId);
        synchronized (mutationLock) {
            StoredMusicSession previous = sessions.remove(guildId);
            if (previous == null) {
                return;
            }
            try {
                persist();
            } catch (RuntimeException exception) {
                sessions.put(guildId, previous);
                throw exception;
            }
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        sessions.values().stream()
                .sorted(Comparator.comparingLong(StoredMusicSession::guildId))
                .map(this::encodeSession)
                .forEach(lines::add);

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            setOwnerOnly(temp);
            try {
                Files.move(temp, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnly(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist music session checkpoints to " + file, exception);
        }
    }

    private String encodeSession(StoredMusicSession session) {
        String current = session.currentTrack() == null ? "-" : encodeTrack(session.currentTrack());
        String queue = encodeTracks(session.queue());
        String history = encodeTracks(session.history());
        return String.join("\t",
                "S",
                Long.toString(session.guildId()),
                Long.toString(session.voiceChannelId()),
                Long.toString(session.capturedAtEpochMillis()),
                Boolean.toString(session.paused()),
                Integer.toString(session.volume()),
                session.repeatMode().name(),
                current,
                queue,
                history);
    }

    private StoredMusicSession decodeSession(String line, boolean legacyV1) {
        String[] parts = line.split("\\t", -1);
        int expectedColumns = legacyV1 ? 9 : 10;
        if (parts.length != expectedColumns || !"S".equals(parts[0])) {
            throw new IllegalArgumentException("expected " + expectedColumns + "-column session line");
        }
        StoredSessionTrack current = "-".equals(parts[7]) ? null : decodeTrack(parts[7]);
        List<StoredSessionTrack> queue = decodeTracks(parts[8]);
        List<StoredSessionTrack> history = legacyV1 ? List.of() : decodeTracks(parts[9]);
        return new StoredMusicSession(
                Long.parseLong(parts[1]),
                Long.parseLong(parts[2]),
                Long.parseLong(parts[3]),
                parseBoolean(parts[4]),
                Integer.parseInt(parts[5]),
                RepeatMode.valueOf(parts[6]),
                current,
                queue,
                history);
    }

    private String encodeTracks(List<StoredSessionTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return "-";
        }
        return tracks.stream().map(this::encodeTrack).reduce((a, b) -> a + ";" + b).orElse("-");
    }

    private List<StoredSessionTrack> decodeTracks(String encodedTracks) {
        if ("-".equals(encodedTracks)) {
            return List.of();
        }
        List<StoredSessionTrack> tracks = new ArrayList<>();
        for (String encoded : encodedTracks.split(";")) {
            if (!encoded.isBlank()) {
                tracks.add(decodeTrack(encoded));
            }
        }
        return List.copyOf(tracks);
    }

    private String encodeTrack(StoredSessionTrack sessionTrack) {
        StoredTrack track = sessionTrack.track();
        return String.join(",",
                encode(track.title()),
                encode(track.author()),
                encode(track.playbackIdentifier()),
                encode(track.sourceIdentifier()),
                track.provider().name(),
                Long.toString(track.durationMillis()),
                Long.toString(track.requesterUserId()),
                encode(track.requesterDisplayName()),
                Long.toString(track.capturedAtEpochMillis()),
                Long.toString(sessionTrack.positionMillis()));
    }

    private StoredSessionTrack decodeTrack(String encoded) {
        String[] parts = encoded.split(",", -1);
        if (parts.length != 10) {
            throw new IllegalArgumentException("expected 10-column track checkpoint");
        }
        String playbackIdentifier = decode(parts[2]);
        MediaProvider provider = MediaProvider.valueOf(parts[4]);
        MediaProvider actualProvider = MediaProvider.fromUri(playbackIdentifier);
        if ((provider != MediaProvider.YOUTUBE && provider != MediaProvider.SOUNDCLOUD)
                || actualProvider != provider) {
            throw new IllegalArgumentException("checkpoint playback identifier/provider mismatch");
        }
        StoredTrack track = new StoredTrack(
                decode(parts[0]),
                decode(parts[1]),
                playbackIdentifier,
                decode(parts[3]),
                provider,
                Long.parseLong(parts[5]),
                Long.parseLong(parts[6]),
                decode(parts[7]),
                Long.parseLong(parts[8]));
        return new StoredSessionTrack(track, Long.parseLong(parts[9]));
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("expected strict boolean value");
    }

    private static String encode(String value) {
        return ENCODER.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static void setOwnerOnly(Path path) {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-POSIX file systems do not expose POSIX permissions.
        } catch (IOException exception) {
            log.warn("Cannot restrict music session checkpoint permissions for {}: {}",
                    path,
                    exception.getMessage());
        }
    }

    private static void validateGuildId(long guildId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
    }
}
