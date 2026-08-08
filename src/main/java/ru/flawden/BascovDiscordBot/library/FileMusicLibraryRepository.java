package ru.flawden.BascovDiscordBot.library;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.MusicLibraryProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Атомарное TSV-хранилище плейлистов и истории без внешней БД.
 *
 * <p>Пользовательские строки кодируются Base64 URL-safe, поэтому табы,
 * переводы строк и Unicode не могут повредить структуру файла.</p>
 */
@Slf4j
@Repository
public class FileMusicLibraryRepository implements MusicLibraryRepository {

    private static final String HEADER = "BASKOV_MUSIC_LIBRARY_V1";
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Map<Long, GuildLibrary> libraries = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();
    private final Path file;

    public FileMusicLibraryRepository(MusicLibraryProperties properties) {
        this.file = properties.getFile().toAbsolutePath().normalize();
    }

    @PostConstruct
    public void load() {
        synchronized (mutationLock) {
            libraries.clear();
            if (Files.notExists(file)) {
                log.info("Music library storage will be created on first change: {}", file);
                return;
            }

            final List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read music library from " + file, exception);
            }
            if (lines.isEmpty()) {
                log.warn("Music library file is empty: {}", file);
                return;
            }
            if (!HEADER.equals(lines.get(0))) {
                throw new IllegalStateException("Unsupported music library format in " + file);
            }

            Map<Long, MutableGuildLibrary> loaded = new HashMap<>();
            for (int index = 1; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                try {
                    loadLine(loaded, line);
                } catch (RuntimeException exception) {
                    log.warn("Ignoring malformed music library line {}: {}", index + 1, exception.getMessage());
                }
            }

            loaded.forEach((guildId, mutable) -> libraries.put(guildId, mutable.toImmutable()));
            int playlistCount = libraries.values().stream()
                    .mapToInt(library -> library.playlists().size())
                    .sum();
            int historyCount = libraries.values().stream()
                    .mapToInt(library -> library.history().size())
                    .sum();
            log.info("Loaded {} playlists and {} history entries for {} Discord guilds from {}",
                    playlistCount,
                    historyCount,
                    libraries.size(),
                    file);
        }
    }

    @Override
    public List<StoredPlaylist> playlists(long guildId) {
        validateGuildId(guildId);
        GuildLibrary library = libraries.getOrDefault(guildId, GuildLibrary.empty());
        return library.playlists().values().stream()
                .sorted(Comparator.comparing(StoredPlaylist::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Optional<StoredPlaylist> playlist(long guildId, String name) {
        validateGuildId(guildId);
        String key = PlaylistName.key(name);
        return Optional.ofNullable(libraries
                .getOrDefault(guildId, GuildLibrary.empty())
                .playlists()
                .get(key));
    }

    @Override
    public PlaylistOperationResult createPlaylist(long guildId, long ownerUserId, String name) {
        validateGuildId(guildId);
        if (ownerUserId <= 0L) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        String displayName = PlaylistName.display(name);
        String key = PlaylistName.key(displayName);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            if (previous.playlists().containsKey(key)) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.ALREADY_EXISTS,
                        previous.playlists().get(key));
            }
            if (previous.playlists().size() >= MAX_PLAYLISTS_PER_GUILD) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.PLAYLIST_LIMIT_REACHED,
                        null);
            }

            StoredPlaylist created = new StoredPlaylist(
                    displayName,
                    ownerUserId,
                    System.currentTimeMillis(),
                    List.of());
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.put(key, created);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(PlaylistOperationResult.Status.CREATED, created);
        }
    }

    @Override
    public PlaylistOperationResult addTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            StoredTrack track) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        if (track == null) {
            return PlaylistOperationResult.of(
                    PlaylistOperationResult.Status.UNREPLAYABLE_TRACK,
                    null);
        }
        String key = PlaylistName.key(name);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }
            if (playlist.tracks().size() >= MAX_TRACKS_PER_PLAYLIST) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.TRACK_LIMIT_REACHED,
                        playlist);
            }

            StoredPlaylist updated = playlist.withAddedTrack(track);
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.put(key, updated);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(PlaylistOperationResult.Status.ADDED, updated, track);
        }
    }

    @Override
    public PlaylistOperationResult addTracks(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            List<StoredTrack> tracks) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String key = PlaylistName.key(name);
        List<StoredTrack> safeTracks = tracks == null
                ? List.of()
                : tracks.stream().filter(java.util.Objects::nonNull).toList();
        if (safeTracks.isEmpty()) {
            return PlaylistOperationResult.of(
                    PlaylistOperationResult.Status.UNREPLAYABLE_TRACK,
                    null);
        }

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }
            if (playlist.tracks().size() + safeTracks.size() > MAX_TRACKS_PER_PLAYLIST) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.TRACK_LIMIT_REACHED,
                        playlist);
            }

            StoredPlaylist updated = playlist.withAddedTracks(safeTracks);
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.put(key, updated);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(
                    PlaylistOperationResult.Status.BULK_ADDED,
                    updated,
                    safeTracks.get(safeTracks.size() - 1),
                    safeTracks.size());
        }
    }

    @Override
    public PlaylistOperationResult renamePlaylist(
            long guildId,
            String name,
            String newName,
            long actorUserId,
            boolean administrator) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String key = PlaylistName.key(name);
        String displayNewName = PlaylistName.display(newName);
        String newKey = PlaylistName.key(displayNewName);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }
            if (!key.equals(newKey) && previous.playlists().containsKey(newKey)) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.ALREADY_EXISTS,
                        previous.playlists().get(newKey));
            }

            StoredPlaylist updated = playlist.withName(displayNewName);
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.remove(key);
            playlists.put(newKey, updated);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(PlaylistOperationResult.Status.RENAMED, updated);
        }
    }

    @Override
    public PlaylistOperationResult copyPlaylist(
            long guildId,
            String sourceName,
            String newName,
            long actorUserId) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String sourceKey = PlaylistName.key(sourceName);
        String displayNewName = PlaylistName.display(newName);
        String newKey = PlaylistName.key(displayNewName);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist source = previous.playlists().get(sourceKey);
            if (source == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (previous.playlists().containsKey(newKey)) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.ALREADY_EXISTS,
                        previous.playlists().get(newKey));
            }
            if (previous.playlists().size() >= MAX_PLAYLISTS_PER_GUILD) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.PLAYLIST_LIMIT_REACHED,
                        null);
            }

            StoredPlaylist copied = new StoredPlaylist(
                    displayNewName,
                    actorUserId,
                    System.currentTimeMillis(),
                    source.tracks());
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.put(newKey, copied);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(
                    PlaylistOperationResult.Status.COPIED,
                    copied,
                    null,
                    copied.tracks().size());
        }
    }

    @Override
    public PlaylistOperationResult moveTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            int fromOneBasedPosition,
            int toOneBasedPosition) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String key = PlaylistName.key(name);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }
            int fromIndex = fromOneBasedPosition - 1;
            int toIndex = toOneBasedPosition - 1;
            if (fromIndex < 0 || fromIndex >= playlist.tracks().size()
                    || toIndex < 0 || toIndex >= playlist.tracks().size()) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.INVALID_POSITION,
                        playlist);
            }

            ArrayList<StoredTrack> tracks = new ArrayList<>(playlist.tracks());
            StoredTrack moved = tracks.remove(fromIndex);
            tracks.add(toIndex, moved);
            StoredPlaylist updated = playlist.withTracks(tracks);
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.put(key, updated);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(PlaylistOperationResult.Status.MOVED, updated, moved);
        }
    }

    @Override
    public PlaylistOperationResult dedupePlaylist(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String key = PlaylistName.key(name);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }

            LinkedHashSet<String> seen = new LinkedHashSet<>();
            ArrayList<StoredTrack> unique = new ArrayList<>();
            for (StoredTrack track : playlist.tracks()) {
                String identity = track.provider().name() + "|"
                        + track.playbackIdentifier().trim().toLowerCase(Locale.ROOT);
                if (seen.add(identity)) {
                    unique.add(track);
                }
            }
            int removed = playlist.tracks().size() - unique.size();
            StoredPlaylist updated = removed == 0 ? playlist : playlist.withTracks(unique);
            if (removed > 0) {
                LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
                playlists.put(key, updated);
                replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            }
            return PlaylistOperationResult.of(
                    PlaylistOperationResult.Status.DEDUPED,
                    updated,
                    null,
                    removed);
        }
    }

    @Override
    public List<PlaylistSearchHit> search(long guildId, String query) {
        validateGuildId(guildId);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Поисковый запрос не может быть пустым");
        }

        return playlists(guildId).stream()
                .map(playlist -> {
                    ArrayList<Integer> positions = new ArrayList<>();
                    for (int index = 0; index < playlist.tracks().size(); index++) {
                        StoredTrack track = playlist.tracks().get(index);
                        if (containsIgnoreCase(track.title(), normalized)
                                || containsIgnoreCase(track.author(), normalized)) {
                            positions.add(index + 1);
                        }
                    }
                    boolean playlistNameMatches = containsIgnoreCase(playlist.name(), normalized);
                    return playlistNameMatches || !positions.isEmpty()
                            ? new PlaylistSearchHit(playlist, positions)
                            : null;
                })
                .filter(java.util.Objects::nonNull)
                .limit(20)
                .toList();
    }

    private static boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    @Override
    public PlaylistOperationResult removeTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            int oneBasedPosition) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String key = PlaylistName.key(name);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }
            int index = oneBasedPosition - 1;
            if (index < 0 || index >= playlist.tracks().size()) {
                return PlaylistOperationResult.of(
                        PlaylistOperationResult.Status.INVALID_POSITION,
                        playlist);
            }

            StoredTrack removed = playlist.tracks().get(index);
            StoredPlaylist updated = playlist.withoutTrack(index);
            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.put(key, updated);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(PlaylistOperationResult.Status.REMOVED, updated, removed);
        }
    }

    @Override
    public PlaylistOperationResult deletePlaylist(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator) {
        validateGuildId(guildId);
        validateActor(actorUserId);
        String key = PlaylistName.key(name);

        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            StoredPlaylist playlist = previous.playlists().get(key);
            if (playlist == null) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.NOT_FOUND, null);
            }
            if (!canModify(playlist, actorUserId, administrator)) {
                return PlaylistOperationResult.of(PlaylistOperationResult.Status.FORBIDDEN, playlist);
            }

            LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>(previous.playlists());
            playlists.remove(key);
            replaceAndPersist(guildId, previous, new GuildLibrary(playlists, previous.history()));
            return PlaylistOperationResult.of(PlaylistOperationResult.Status.DELETED, playlist);
        }
    }

    @Override
    public List<StoredTrack> history(long guildId) {
        validateGuildId(guildId);
        return libraries.getOrDefault(guildId, GuildLibrary.empty()).history();
    }

    @Override
    public void recordHistory(long guildId, StoredTrack track) {
        validateGuildId(guildId);
        if (track == null) {
            return;
        }
        synchronized (mutationLock) {
            GuildLibrary previous = libraries.getOrDefault(guildId, GuildLibrary.empty());
            ArrayList<StoredTrack> history = new ArrayList<>(previous.history().size() + 1);
            history.add(track);
            history.addAll(previous.history());
            if (history.size() > MAX_HISTORY_PER_GUILD) {
                history.subList(MAX_HISTORY_PER_GUILD, history.size()).clear();
            }
            replaceAndPersist(guildId, previous, new GuildLibrary(previous.playlists(), history));
        }
    }

    private void replaceAndPersist(long guildId, GuildLibrary previous, GuildLibrary updated) {
        libraries.put(guildId, updated);
        try {
            persistLocked();
        } catch (RuntimeException exception) {
            if (previous.isEmpty()) {
                libraries.remove(guildId);
            } else {
                libraries.put(guildId, previous);
            }
            throw exception;
        }
    }

    private void loadLine(Map<Long, MutableGuildLibrary> loaded, String line) {
        String[] columns = line.split("\\t", -1);
        switch (columns[0]) {
            case "P" -> loadPlaylist(loaded, columns);
            case "T" -> loadPlaylistTrack(loaded, columns);
            case "H" -> loadHistory(loaded, columns);
            default -> throw new IllegalArgumentException("unknown record type " + columns[0]);
        }
    }

    private void loadPlaylist(Map<Long, MutableGuildLibrary> loaded, String[] columns) {
        requireColumns(columns, 5);
        long guildId = positiveLong(columns[1], "guildId");
        long ownerUserId = positiveLong(columns[2], "ownerUserId");
        long createdAt = positiveLong(columns[3], "createdAt");
        String name = decode(columns[4]);

        MutableGuildLibrary library = loaded.computeIfAbsent(guildId, ignored -> new MutableGuildLibrary());
        if (library.playlists.size() >= MAX_PLAYLISTS_PER_GUILD) {
            return;
        }
        StoredPlaylist playlist = new StoredPlaylist(name, ownerUserId, createdAt, List.of());
        library.playlists.putIfAbsent(playlist.key(), playlist);
    }

    private void loadPlaylistTrack(Map<Long, MutableGuildLibrary> loaded, String[] columns) {
        requireColumns(columns, 13);
        long guildId = positiveLong(columns[1], "guildId");
        String playlistKey = decode(columns[2]);
        int position = nonNegativeInt(columns[3], "position");
        StoredTrack track = decodeTrack(columns, 4);

        MutableGuildLibrary library = loaded.computeIfAbsent(guildId, ignored -> new MutableGuildLibrary());
        StoredPlaylist playlist = library.playlists.get(playlistKey);
        if (playlist == null || position != playlist.tracks().size()
                || playlist.tracks().size() >= MAX_TRACKS_PER_PLAYLIST) {
            return;
        }
        library.playlists.put(playlistKey, playlist.withAddedTrack(track));
    }

    private void loadHistory(Map<Long, MutableGuildLibrary> loaded, String[] columns) {
        requireColumns(columns, 12);
        long guildId = positiveLong(columns[1], "guildId");
        int position = nonNegativeInt(columns[2], "position");
        StoredTrack track = decodeTrack(columns, 3);

        MutableGuildLibrary library = loaded.computeIfAbsent(guildId, ignored -> new MutableGuildLibrary());
        if (position == library.history.size() && library.history.size() < MAX_HISTORY_PER_GUILD) {
            library.history.add(track);
        }
    }

    private StoredTrack decodeTrack(String[] columns, int offset) {
        return new StoredTrack(
                decode(columns[offset]),
                decode(columns[offset + 1]),
                decode(columns[offset + 2]),
                decode(columns[offset + 3]),
                MediaProvider.valueOf(columns[offset + 4]),
                positiveLong(columns[offset + 5], "durationMillis"),
                nonNegativeLong(columns[offset + 6], "requesterUserId"),
                decode(columns[offset + 7]),
                positiveLong(columns[offset + 8], "capturedAtEpochMillis"));
    }

    private void persistLocked() {
        Path parent = file.getParent();
        if (parent == null) {
            throw new IllegalStateException("Music library file has no parent directory: " + file);
        }

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        libraries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendGuild(lines, entry.getKey(), entry.getValue()));

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            setOwnerOnlyPermissions(temporary);
            moveAtomically(temporary, file);
            setOwnerOnlyPermissions(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist music library to " + file, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.warn("Cannot delete temporary music library file {}", temporary, exception);
                }
            }
        }
    }

    private void appendGuild(List<String> lines, long guildId, GuildLibrary library) {
        library.playlists().values().stream()
                .sorted(Comparator.comparing(StoredPlaylist::key))
                .forEach(playlist -> {
                    lines.add(String.join("\t",
                            "P",
                            Long.toString(guildId),
                            Long.toString(playlist.ownerUserId()),
                            Long.toString(playlist.createdAtEpochMillis()),
                            encode(playlist.name())));
                    for (int index = 0; index < playlist.tracks().size(); index++) {
                        List<String> columns = new ArrayList<>();
                        columns.add("T");
                        columns.add(Long.toString(guildId));
                        columns.add(encode(playlist.key()));
                        columns.add(Integer.toString(index));
                        appendTrack(columns, playlist.tracks().get(index));
                        lines.add(String.join("\t", columns));
                    }
                });
        for (int index = 0; index < library.history().size(); index++) {
            List<String> columns = new ArrayList<>();
            columns.add("H");
            columns.add(Long.toString(guildId));
            columns.add(Integer.toString(index));
            appendTrack(columns, library.history().get(index));
            lines.add(String.join("\t", columns));
        }
    }

    private static void appendTrack(List<String> columns, StoredTrack track) {
        columns.add(encode(track.title()));
        columns.add(encode(track.author()));
        columns.add(encode(track.playbackIdentifier()));
        columns.add(encode(track.sourceIdentifier()));
        columns.add(track.provider().name());
        columns.add(Long.toString(track.durationMillis()));
        columns.add(Long.toString(track.requesterUserId()));
        columns.add(encode(track.requesterDisplayName()));
        columns.add(Long.toString(track.capturedAtEpochMillis()));
    }

    private static boolean canModify(
            StoredPlaylist playlist,
            long actorUserId,
            boolean administrator) {
        return administrator || playlist.ownerUserId() == actorUserId;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid Base64 field", exception);
        }
    }

    private static void requireColumns(String[] columns, int expected) {
        if (columns.length != expected) {
            throw new IllegalArgumentException(
                    "expected " + expected + " columns, got " + columns.length);
        }
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

    private static void validateGuildId(long guildId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
    }

    private static void validateActor(long actorUserId) {
        if (actorUserId <= 0L) {
            throw new IllegalArgumentException("actorUserId must be positive");
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

    private record GuildLibrary(
            Map<String, StoredPlaylist> playlists,
            List<StoredTrack> history) {

        private GuildLibrary {
            playlists = Collections.unmodifiableMap(new LinkedHashMap<>(playlists));
            history = List.copyOf(history);
        }

        private static GuildLibrary empty() {
            return new GuildLibrary(Map.of(), List.of());
        }

        private boolean isEmpty() {
            return playlists.isEmpty() && history.isEmpty();
        }
    }

    private static final class MutableGuildLibrary {
        private final LinkedHashMap<String, StoredPlaylist> playlists = new LinkedHashMap<>();
        private final ArrayList<StoredTrack> history = new ArrayList<>();

        private GuildLibrary toImmutable() {
            return new GuildLibrary(playlists, history);
        }
    }
}
