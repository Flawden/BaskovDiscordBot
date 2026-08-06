package ru.flawden.BascovDiscordBot.session;

import java.util.List;
import java.util.Optional;

/**
 * Постоянные checkpoints активных музыкальных сессий.
 */
public interface MusicSessionRepository {

    List<StoredMusicSession> sessions();

    Optional<StoredMusicSession> session(long guildId);

    void save(StoredMusicSession session);

    void remove(long guildId);
}
