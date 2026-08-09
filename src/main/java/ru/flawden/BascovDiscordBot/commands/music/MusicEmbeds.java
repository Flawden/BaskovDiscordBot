package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.BatchMusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.PlaybackReadinessResult;
import ru.flawden.BascovDiscordBot.lavaplayer.QueueCollaboration;
import ru.flawden.BascovDiscordBot.lavaplayer.QueuePage;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackScheduler;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;
import ru.flawden.BascovDiscordBot.lavaplayer.VoiceConnectionResult;
import ru.flawden.BascovDiscordBot.library.FavoriteSearchHit;
import ru.flawden.BascovDiscordBot.library.PersonalArtistStat;
import ru.flawden.BascovDiscordBot.library.PersonalListeningInsights;
import ru.flawden.BascovDiscordBot.library.PersonalTrackStat;
import ru.flawden.BascovDiscordBot.library.PlaylistSearchHit;
import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Единое представление музыкальных ответов для prefix- и slash-команд.
 */
public final class MusicEmbeds {

    private MusicEmbeds() {
    }

    public static MessageEmbed loadResult(MusicLoadResult result, MusicProperties properties) {
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.GREEN);
        AudioTrack track = result.track();
        String requester = requesterLabel(result.requester());

        switch (result.status()) {
            case STARTED -> embed
                    .setColor(Color.ORANGE)
                    .setTitle("⏳ Трек загружен")
                    .setDescription(formatTrack(track)
                            + "\n**Источник:** `" + providerLabel(track) + "`"
                            + "\n**Заказал:** " + requester
                            + "\nПроверяю, что Discord DAVE/media transport реально запрашивает аудиофреймы...");
            case QUEUED -> embed
                    .setTitle("🎶 Добавлено в очередь")
                    .setDescription(formatTrack(track)
                            + "\n**Источник:** `" + providerLabel(track) + "`"
                            + "\n**Заказал:** " + requester
                            + "\n**Позиция:** `" + result.queuePosition() + "`"
                            + "\n**Примерно начнётся через:** `"
                            + humanMillis(result.estimatedWaitMillis()) + "`");
            case REQUESTER_LIMIT -> embed
                    .setColor(Color.RED)
                    .setTitle("👤 Персональный лимит очереди")
                    .setDescription("У тебя уже максимально разрешённое число ожидающих треков. "
                            + "Дождись начала одного из них или удали свой трек через `/queue-manage mine`.");
            case QUEUE_FULL -> embed
                    .setColor(Color.RED)
                    .setTitle("🚧 Очередь заполнена")
                    .setDescription("В очереди уже `" + properties.getMaxQueueSize()
                            + "` треков. Дождись свободного места.");
            case TRACK_TOO_LONG -> embed
                    .setColor(Color.RED)
                    .setTitle("⏱️ Трек слишком длинный")
                    .setDescription("Максимальная длительность трека: `"
                            + humanDuration(properties.getMaxTrackDuration()) + "`.");
            case STREAM_NOT_ALLOWED -> embed
                    .setColor(Color.RED)
                    .setTitle("📡 Поток не поддерживается")
                    .setDescription("Прямые трансляции отключены, чтобы музыкальная сессия не зависала навсегда.");
            case NO_MATCHES -> embed
                    .setColor(Color.RED)
                    .setTitle("❌ Песня не найдена")
                    .setDescription("Проверь название или ссылку и попробуй снова.");
            case LOAD_FAILED -> embed
                    .setColor(Color.RED)
                    .setTitle("❌ Ошибка загрузки")
                    .setDescription("Не удалось загрузить трек. Попробуй снова чуть позже.");
            case SESSION_CLOSED -> embed
                    .setColor(Color.RED)
                    .setTitle("🛑 Сессия уже закрыта")
                    .setDescription("Трек загрузился после остановки музыкальной сессии и был проигнорирован.");
        }
        return embed.build();
    }


    public static MessageEmbed playbackConfirmed(MusicLoadResult result) {
        return new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("▶️ Воспроизведение подтверждено")
                .setDescription(formatTrack(result.track())
                        + "\n**Источник:** `" + providerLabel(result.track()) + "`"
                        + "\n**Заказал:** " + requesterLabel(result.requester())
                        + "\nDiscord начал принимать аудиофреймы.")
                .build();
    }

    public static MessageEmbed playbackReadinessFailure(
            PlaybackReadinessResult readiness,
            String jdaVersion) {
        String details = readiness == null || readiness.details() == null
                ? "Не удалось подтвердить Discord media transport."
                : readiness.details();
        String diagnosis = readiness == null
                ? "UNKNOWN"
                : readiness.status().name();

        String hint = switch (readiness == null
                ? PlaybackReadinessResult.Status.FRAME_TIMEOUT
                : readiness.status()) {
            case VOICE_LEFT -> "Discord завершил voice handshake до первого аудиофрейма. "
                    + "Проверь `/status`: DAVE/E2EE должен быть READY с protocol version > 0.";
            case FRAME_TIMEOUT -> "Voice control подключился, но media transport не начал polling аудио.";
            case SESSION_CLOSED -> "Сессия была остановлена другой командой или во время перезапуска.";
            case TRACK_REPLACED -> "Трек был заменён до завершения проверки транспорта.";
            case READY -> "Discord media transport работает.";
        };

        return new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("🔐 Discord voice transport не подтвердился")
                .setDescription(details
                        + "\n**Диагноз:** `" + diagnosis + "`"
                        + "\n**JDA:** `" + (jdaVersion == null ? "unknown" : jdaVersion) + "`"
                        + "\n" + hint
                        + "\nБот не будет показывать ложное сообщение о начале воспроизведения.")
                .build();
    }

    public static MessageEmbed voiceConnectionFailure(VoiceConnectionResult result) {
        String details = result == null || result.details() == null || result.details().isBlank()
                ? "Не удалось установить стабильное голосовое соединение."
                : result.details();

        if (result == null) {
            return error("🔌 Голосовое соединение не установлено", details);
        }

        return switch (result.status()) {
            case TIMEOUT -> error(
                    "⏳ Голосовое подключение не удалось",
                    details + "\nБот не будет бесконечно переподключаться. Повтори команду чуть позже.");
            case COOLDOWN -> error(
                    "🧊 Подключение временно приостановлено",
                    details);
            case BUSY -> error(
                    "🚧 Подключение уже выполняется",
                    details);
            case SHUTTING_DOWN -> error(
                    "🛑 Бот перезапускается",
                    "Музыкальный сервис сейчас завершает работу. Повтори команду после перезапуска.");
            case FAILED -> error(
                    "🔌 Голосовое соединение сорвалось",
                    details + "\nСессия закрыта, чтобы бот не входил и не выходил из канала по кругу.");
            case CONNECTED -> success(
                    "🔌 Голосовое соединение установлено",
                    details);
        };
    }

    public static MessageEmbed searchResults(
            String query,
            List<AudioTrack> candidates,
            Instant expiresAt) {
        if (candidates == null || candidates.isEmpty()) {
            return error("🔎 Ничего не найдено", "Проверь запрос и попробуй снова.");
        }

        StringBuilder description = new StringBuilder()
                .append("**Запрос:** `")
                .append(shortText(query, 120))
                .append("`\n\n");
        for (int index = 0; index < candidates.size(); index++) {
            AudioTrack track = candidates.get(index);
            description.append("**").append(index + 1).append(". ")
                    .append(shortText(track.getInfo().title, 90)).append("**\n")
                    .append(shortText(track.getInfo().author, 70))
                    .append(" • `").append(formatTime(track.getDuration())).append("`")
                    .append(" • `").append(providerLabel(track)).append("`\n\n");
        }
        description.append("Нажми кнопку с номером нужного результата.");
        if (expiresAt != null) {
            description.append(" Выбор истечёт <t:")
                    .append(expiresAt.getEpochSecond())
                    .append(":R>.");
        }

        String footer = "Результаты доступны только автору поиска";
        return new EmbedBuilder()
                .setTitle("🔎 Выбери трек")
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .setFooter(footer)
                .build();
    }

    public static MessageEmbed discoveryRecent(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return error(
                    "🧭 Недавних поисков пока нет",
                    "Выполни `/search` или `/play` с текстовым запросом, и Басков запомнит его до перезапуска.");
        }
        StringBuilder description = new StringBuilder();
        for (int index = 0; index < queries.size(); index++) {
            description.append("**").append(index + 1).append(".** `")
                    .append(shortText(queries.get(index), 100))
                    .append("`\n");
        }
        return new EmbedBuilder()
                .setTitle("🧭 Твои недавние поиски")
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .setFooter("/discover again повторит последний запрос • история поиска хранится в памяти процесса")
                .build();
    }

    public static MessageEmbed playbackHistory(List<StoredTrack> history, int requestedPage) {
        return playbackHistory(history, requestedPage, false);
    }

    public static MessageEmbed playbackHistory(List<StoredTrack> history, int requestedPage, boolean personal) {
        if (history == null || history.isEmpty()) {
            return error(
                    "🕘 История пуста",
                    "Завершённые и вручную пропущенные треки появятся здесь после воспроизведения.");
        }
        int pageSize = 10;
        int totalPages = Math.max(1, (history.size() + pageSize - 1) / pageSize);
        if (requestedPage < 1 || requestedPage > totalPages) {
            return error(
                    "📄 Страница истории не найдена",
                    "Доступны страницы `1.." + totalPages + "`.");
        }

        int from = (requestedPage - 1) * pageSize;
        int to = Math.min(history.size(), from + pageSize);
        StringBuilder description = new StringBuilder();
        for (int index = from; index < to; index++) {
            StoredTrack track = history.get(index);
            description.append("**").append(index + 1).append(". ")
                    .append(shortText(track.title(), 90)).append("**\n")
                    .append(shortText(track.author(), 70))
                    .append(" • `").append(formatTime(track.durationMillis())).append("`")
                    .append(" • `").append(track.provider().label()).append("`")
                    .append(" • ").append(storedRequesterLabel(track))
                    .append("\n<t:").append(track.capturedAtEpochMillis() / 1000L).append(":R>\n\n");
        }

        return new EmbedBuilder()
                .setTitle((personal ? "👤 Твоя история" : "🕘 История воспроизведения")
                        + " • " + requestedPage + "/" + totalPages)
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .setFooter(personal
                        ? "Номер подходит для /replay position scope:mine • хранится до 200 твоих записей"
                        : "Номер подходит для /replay position • хранится до 50 треков сервера")
                .build();
    }

    public static MessageEmbed personalListeningProfile(List<StoredTrack> personalHistory, int favoriteCount) {
        if (personalHistory == null || personalHistory.isEmpty()) {
            return error(
                    "👤 Личный профиль пока пуст",
                    "Твои заказанные треки появятся после воспроизведения. Favorites уже можно сохранять через `/favorites add`.");
        }

        List<PersonalTrackStat> topTracks = PersonalListeningInsights.topTracks(personalHistory, 5);
        List<PersonalArtistStat> topArtists = PersonalListeningInsights.topArtists(personalHistory, 5);
        StringBuilder tracks = new StringBuilder();
        for (int index = 0; index < topTracks.size(); index++) {
            PersonalTrackStat stat = topTracks.get(index);
            tracks.append("**").append(index + 1).append(". ")
                    .append(shortText(stat.track().title(), 70)).append("** — `")
                    .append(stat.plays()).append("×`\n")
                    .append(shortText(stat.track().author(), 60)).append("\n");
        }
        StringBuilder artists = new StringBuilder();
        for (int index = 0; index < topArtists.size(); index++) {
            PersonalArtistStat stat = topArtists.get(index);
            artists.append("**").append(index + 1).append(". ")
                    .append(shortText(stat.artist(), 70)).append("** — `")
                    .append(stat.plays()).append("×`\n");
        }
        if (artists.isEmpty()) {
            artists.append("Недостаточно данных об исполнителях.");
        }

        return new EmbedBuilder()
                .setTitle("👤 Твой listening profile")
                .setDescription("Persistent personal history строится только из твоих заказов, которые реально дошли до истории воспроизведения.")
                .addField("🔥 Часто запускал", tracks.toString(), false)
                .addField("🎤 Частые исполнители", artists.toString(), false)
                .addField("📊 Сводка",
                        "Записей: `" + personalHistory.size()
                                + "` • уникальных треков: `" + PersonalListeningInsights.uniqueTrackCount(personalHistory)
                                + "` • favorites: `" + Math.max(0, favoriteCount) + "`",
                        false)
                .setColor(Color.CYAN)
                .setFooter("/discover for-me использует favorites + personal history локально, без отдельного recommendation service")
                .build();
    }

    public static MessageEmbed favorites(List<StoredTrack> favorites, int requestedPage) {
        if (favorites == null || favorites.isEmpty()) {
            return error(
                    "⭐ Избранное пусто",
                    "Запусти музыку и сохрани текущий трек через `/favorites add`.");
        }
        int pageSize = 10;
        int totalPages = Math.max(1, (favorites.size() + pageSize - 1) / pageSize);
        if (requestedPage < 1 || requestedPage > totalPages) {
            return error(
                    "📄 Страница избранного не найдена",
                    "Доступны страницы `1.." + totalPages + "`.");
        }

        int from = (requestedPage - 1) * pageSize;
        int to = Math.min(favorites.size(), from + pageSize);
        StringBuilder description = new StringBuilder();
        for (int index = from; index < to; index++) {
            StoredTrack track = favorites.get(index);
            description.append("**").append(index + 1).append(". ")
                    .append(shortText(track.title(), 90)).append("**\n")
                    .append(shortText(track.author(), 70))
                    .append(" • `").append(formatTime(track.durationMillis())).append("`")
                    .append(" • `").append(track.provider().label()).append("`\n\n");
        }
        long duration = favorites.stream().mapToLong(StoredTrack::durationMillis).sum();
        return new EmbedBuilder()
                .setTitle("⭐ Твоё избранное • " + requestedPage + "/" + totalPages)
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .setFooter("Треков: " + favorites.size()
                        + " • Длительность: " + humanMillis(duration)
                        + " • номера подходят для /favorites play и /favorites remove")
                .build();
    }

    public static MessageEmbed favoriteSearch(String query, List<FavoriteSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return error(
                    "🔎 В избранном ничего не найдено",
                    "По запросу `" + shortText(query, 80) + "` совпадений нет.");
        }
        StringBuilder description = new StringBuilder();
        for (FavoriteSearchHit hit : hits) {
            StoredTrack track = hit.track();
            description.append("**").append(hit.position()).append(". ")
                    .append(shortText(track.title(), 90)).append("**\n")
                    .append(shortText(track.author(), 70))
                    .append(" • `").append(formatTime(track.durationMillis())).append("`")
                    .append(" • `").append(track.provider().label()).append("`\n\n");
        }
        return new EmbedBuilder()
                .setTitle("🔎 Поиск по избранному")
                .setDescription("Запрос: `" + shortText(query, 80) + "`\n\n" + description)
                .setColor(Color.CYAN)
                .setFooter("Позиции совпадают с /favorites list")
                .build();
    }

    public static MessageEmbed playlistList(List<StoredPlaylist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return error(
                    "📚 Плейлистов пока нет",
                    "Создай первый через `/playlist create name:<название>`.");
        }
        StringBuilder description = new StringBuilder();
        for (StoredPlaylist playlist : playlists) {
            long duration = playlist.tracks().stream()
                    .mapToLong(StoredTrack::durationMillis)
                    .sum();
            description.append("• **").append(shortText(playlist.name(), 60)).append("**")
                    .append(" — `").append(playlist.tracks().size()).append(" треков`")
                    .append(" • `").append(humanMillis(duration)).append("`")
                    .append(" • владелец <@").append(playlist.ownerUserId()).append(">\n");
        }
        return new EmbedBuilder()
                .setTitle("📚 Плейлисты сервера")
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .setFooter("До 20 плейлистов на сервер и до 50 треков в каждом")
                .build();
    }

    public static MessageEmbed playlistSearch(String query, List<PlaylistSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return error(
                    "🔎 В библиотеке ничего не найдено",
                    "По запросу `" + shortText(query, 80) + "` совпадений нет.");
        }
        StringBuilder description = new StringBuilder();
        for (PlaylistSearchHit hit : hits) {
            StoredPlaylist playlist = hit.playlist();
            description.append("• **").append(shortText(playlist.name(), 60)).append("**")
                    .append(" — `").append(playlist.tracks().size()).append(" треков`");
            if (!hit.matchingPositions().isEmpty()) {
                description.append(" • позиции `")
                        .append(hit.matchingPositions().stream()
                                .limit(8)
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(", ")))
                        .append(hit.matchingPositions().size() > 8 ? ", …" : "")
                        .append("`");
            }
            description.append("\n");
        }
        return new EmbedBuilder()
                .setTitle("🔎 Поиск по библиотеке")
                .setDescription("Запрос: `" + shortText(query, 80) + "`\n\n" + description)
                .setColor(Color.CYAN)
                .setFooter("Позиции можно открыть через /playlist show")
                .build();
    }

    public static MessageEmbed playlistView(StoredPlaylist playlist, int requestedPage) {
        if (playlist == null) {
            return error("📚 Плейлист не найден", "Проверь название через `/playlist list`.");
        }
        if (playlist.tracks().isEmpty()) {
            return new EmbedBuilder()
                    .setTitle("📚 " + shortText(playlist.name(), 100))
                    .setDescription("Плейлист пуст. Запусти музыку и используй `/playlist add`.")
                    .setColor(Color.CYAN)
                    .setFooter("Владелец: " + playlist.ownerUserId())
                    .build();
        }

        int pageSize = 10;
        int totalPages = Math.max(1, (playlist.tracks().size() + pageSize - 1) / pageSize);
        if (requestedPage < 1 || requestedPage > totalPages) {
            return error(
                    "📄 Страница плейлиста не найдена",
                    "Доступны страницы `1.." + totalPages + "`.");
        }

        int from = (requestedPage - 1) * pageSize;
        int to = Math.min(playlist.tracks().size(), from + pageSize);
        StringBuilder description = new StringBuilder();
        for (int index = from; index < to; index++) {
            StoredTrack track = playlist.tracks().get(index);
            description.append("**").append(index + 1).append(". ")
                    .append(shortText(track.title(), 90)).append("**\n")
                    .append(shortText(track.author(), 70))
                    .append(" • `").append(formatTime(track.durationMillis())).append("`")
                    .append(" • `").append(track.provider().label()).append("`\n\n");
        }
        long duration = playlist.tracks().stream().mapToLong(StoredTrack::durationMillis).sum();
        return new EmbedBuilder()
                .setTitle("📚 " + shortText(playlist.name(), 100)
                        + " • " + requestedPage + "/" + totalPages)
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .setFooter("Треков: " + playlist.tracks().size()
                        + " • Длительность: " + humanMillis(duration)
                        + " • Владелец: " + playlist.ownerUserId())
                .build();
    }

    public static MessageEmbed batchLoadResult(String title, BatchMusicLoadResult result) {
        if (result == null || result.requested() == 0) {
            return error("📭 Нечего загружать", "Список сохранённых треков пуст.");
        }
        String details = "Запрошено: `" + result.requested() + "`"
                + "\nПринято: `" + result.accepted() + "`"
                + "\nЗапущено сейчас: `" + result.started() + "`"
                + "\nДобавлено в очередь: `" + result.queued() + "`"
                + "\nНе удалось загрузить: `" + result.rejected() + "`.";
        return result.accepted() == 0
                ? error("❌ Сохранённые треки не загрузились", details)
                : success(title, details);
    }

    public static MessageEmbed nowPlaying(GuildMusicManager musicManager) {
        AudioPlayer audioPlayer = musicManager == null ? null : musicManager.getAudioPlayer();
        AudioTrack currentTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            return error("🎵 Сейчас тишина", "В данный момент нет воспроизводимых песен.");
        }

        TrackRequest request = musicManager.getScheduler().getCurrentRequest();
        long duration = Math.max(0L, currentTrack.getDuration());
        long position = Math.max(0L, Math.min(currentTrack.getPosition(), duration));
        long remaining = Math.max(0L, duration - position);
        int progressPercent = duration <= 0L
                ? 0
                : (int) Math.min(100L, Math.round(position * 100.0d / duration));
        int previousCount = musicManager.getScheduler().historySize();
        int queuedCount = musicManager.getScheduler().queueSize();
        String controlsHint = (previousCount == 0 || queuedCount < 2)
                ? "\n\nℹ️ **Серые кнопки:** "
                        + (previousCount == 0 ? "`Предыдущий` — история пуста. " : "")
                        + (queuedCount < 2 ? "`Перемешать` — нужно минимум 2 ожидающих трека." : "")
                : "";

        return new EmbedBuilder()
                .setTitle("🎵 Сейчас играет")
                .setColor(Color.CYAN)
                .setDescription("**Название:** `" + shorten(currentTrack.getInfo().title) + "`\n"
                        + "**Автор:** `" + shorten(currentTrack.getInfo().author) + "`\n"
                        + "**Источник:** `" + providerLabel(currentTrack) + "`\n"
                        + "**Заказал:** " + requesterLabel(request == null ? null : request.requester()) + "\n\n"
                        + "`" + progressBar(position, duration, 16) + "` `" + progressPercent + "%`\n"
                        + "**Позиция:** `" + formatTime(position) + " / "
                        + formatTime(duration) + "`\n"
                        + "**Осталось:** `" + humanMillis(remaining) + "`\n"
                        + "**Громкость:** `" + audioPlayer.getVolume() + "%`\n"
                        + "**Повтор:** `" + musicManager.getScheduler().getRepeatMode().label() + "`\n"
                        + "**Предыдущих:** `" + previousCount + "` • **В очереди:** `" + queuedCount + "`\n"
                        + (audioPlayer.isPaused() ? "⚠️ Воспроизведение на паузе" : "▶️ Воспроизведение активно")
                        + controlsHint)
                .build();
    }

    public static MessageEmbed queue(GuildMusicManager musicManager) {
        return queueView(musicManager, 1).embed();
    }

    public static QueueView queueView(GuildMusicManager musicManager, int requestedPage) {
        AudioPlayer audioPlayer = musicManager == null ? null : musicManager.getAudioPlayer();
        AudioTrack playingTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        TrackScheduler.QueueSnapshot queueSnapshot = musicManager == null
                ? new TrackScheduler.QueueSnapshot(0L, List.of(), 0L, 0, 0)
                : musicManager.getScheduler().queueSnapshot();
        List<TrackRequest> requests = queueSnapshot.requests();
        QueuePage page = QueuePage.of(requests, requestedPage);
        TrackScheduler.QueueStats queueStats = new TrackScheduler.QueueStats(
                queueSnapshot.revision(),
                queueSnapshot.requests().size(),
                queueSnapshot.totalDurationMillis(),
                queueSnapshot.uniqueRequesters(),
                queueSnapshot.duplicateCount());

        if (playingTrack == null && requests.isEmpty()) {
            return new QueueView(
                    error("🎶 Очередь пуста", "Сейчас ничего не играет. Добавь песню через `/play`."),
                    page.number(),
                    page.totalPages());
        }

        StringBuilder description = new StringBuilder();
        long queueEta = 0L;
        if (playingTrack != null) {
            TrackRequest current = musicManager.getScheduler().getCurrentRequest();
            long duration = Math.max(0L, playingTrack.getDuration());
            long position = Math.max(0L, Math.min(playingTrack.getPosition(), duration));
            queueEta = Math.max(0L, duration - position);
            description.append("**Текущая песня:**\n")
                    .append('`').append(shorten(playingTrack.getInfo().title)).append("` — ")
                    .append(shorten(playingTrack.getInfo().author)).append('\n')
                    .append("**Источник:** `").append(providerLabel(playingTrack)).append("`\n")
                    .append("`").append(progressBar(position, duration, 12)).append("` ")
                    .append('`').append(formatTime(position)).append(" / ")
                    .append(formatTime(duration)).append("`\n")
                    .append("**Заказал:** ")
                    .append(requesterLabel(current == null ? null : current.requester()))
                    .append(audioPlayer.isPaused() ? "\n⚠️ Воспроизведение на паузе" : "")
                    .append("\n\n");
        }

        if (requests.isEmpty()) {
            description.append("**Очередь:**\nСписок следующих песен пуст.");
        } else {
            for (int index = 0; index < Math.max(0, page.firstPosition() - 1); index++) {
                queueEta += safeDuration(requests.get(index).track());
            }

            description.append("**Очередь (").append(requests.size()).append("):**\n");
            for (int index = 0; index < page.items().size(); index++) {
                TrackRequest request = page.items().get(index);
                AudioTrack track = request.track();
                int globalPosition = page.firstPosition() + index;
                description.append(globalPosition).append(". `")
                        .append(shorten(track.getInfo().title)).append("` — ")
                        .append(formatTime(track.getDuration()))
                        .append(" • ").append(providerLabel(track))
                        .append(" • ").append(requesterLabel(request.requester()))
                        .append(" • через `").append(humanMillis(queueEta)).append("`\n");
                queueEta += safeDuration(track);
            }
        }

        if (musicManager != null) {
            description.append("\n**Состояние сессии:**\n")
                    .append("Громкость: `").append(audioPlayer.getVolume()).append("%` • ")
                    .append("Повтор: `").append(musicManager.getScheduler().getRepeatMode().label()).append("` • ")
                    .append("Предыдущих: `").append(musicManager.getScheduler().historySize()).append("`\n")
                    .append("Ревизия очереди: `").append(queueStats.revision()).append("` • ")
                    .append("Заказчиков: `").append(queueStats.uniqueRequesters()).append("` • ")
                    .append("Дубликатов: `").append(queueStats.duplicateCount()).append("`\n")
                    .append("Длительность ожидания: `")
                    .append(humanMillis(queueStats.totalDurationMillis())).append("` • ")
                    .append("До конца текущей очереди: `")
                    .append(humanMillis(musicManager.getScheduler().estimatedWaitMillis())).append('`');
        }

        String footer = requests.isEmpty()
                ? "Страница 1/1 • Ревизия " + queueStats.revision()
                : "Показано " + page.firstPosition() + "–" + page.lastPosition()
                + " из " + page.totalItems() + " • Страница "
                + page.number() + "/" + page.totalPages()
                + " • Ревизия " + queueStats.revision()
                + " • Номера подходят для /remove и /move; диапазоны — /queue-manage remove-range";

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("🎶 Список треков • страница " + page.number() + "/" + page.totalPages())
                .setDescription(description.toString())
                .setFooter(footer)
                .setColor(Color.CYAN)
                .build();
        return new QueueView(embed, page.number(), page.totalPages());
    }

    public record QueueView(MessageEmbed embed, int page, int totalPages) {
    }


    public static MessageEmbed personalQueue(GuildMusicManager musicManager, long userId) {
        TrackScheduler.QueueSnapshot snapshot = musicManager == null
                ? new TrackScheduler.QueueSnapshot(0L, List.of(), 0L, 0, 0)
                : musicManager.getScheduler().queueSnapshot();
        QueueCollaboration.Summary summary = QueueCollaboration.summarize(snapshot.requests(), userId);
        if (summary.ownedTracks().isEmpty()) {
            return success(
                    "👤 Твоя очередь пуста",
                    "У тебя нет ожидающих треков. Общая очередь: `" + summary.totalTracks()
                            + "` • ревизия `" + snapshot.revision() + "`.");
        }

        StringBuilder description = new StringBuilder();
        for (QueueCollaboration.OwnedTrack item : summary.ownedTracks()) {
            AudioTrack track = item.request().track();
            description.append(item.globalPosition()).append(". `")
                    .append(shorten(track.getInfo().title)).append("` — ")
                    .append(formatTime(track.getDuration())).append(" • ")
                    .append(providerLabel(track)).append('\n');
        }
        description.append("\n**Твоих треков:** `").append(summary.ownedTracks().size()).append("` из `")
                .append(summary.totalTracks()).append("`\n")
                .append("**Твоя длительность:** `").append(humanMillis(summary.ownDurationMillis())).append("`\n")
                .append("**Ревизия:** `").append(snapshot.revision()).append("`\n\n")
                .append("Удалить один свой трек: `/queue-manage remove-own position:<n> revision:")
                .append(snapshot.revision()).append("`.");
        return new EmbedBuilder()
                .setTitle("👤 Твои треки в общей очереди")
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .build();
    }

    public static MessageEmbed queueCommunity(GuildMusicManager musicManager) {
        TrackScheduler.QueueSnapshot snapshot = musicManager == null
                ? new TrackScheduler.QueueSnapshot(0L, List.of(), 0L, 0, 0)
                : musicManager.getScheduler().queueSnapshot();
        QueueCollaboration.Summary summary = QueueCollaboration.summarize(snapshot.requests(), 0L);
        if (summary.contributors().isEmpty()) {
            return success("👥 Заказчиков пока нет", "Ожидающая очередь пуста. Ревизия: `" + snapshot.revision() + "`.");
        }

        StringBuilder description = new StringBuilder();
        int rank = 1;
        for (QueueCollaboration.Contributor contributor : summary.contributors().stream().limit(10).toList()) {
            description.append(rank++).append(". ").append(contributor.discordLabel())
                    .append(" — `").append(contributor.trackCount()).append("` трек(а), `")
                    .append(humanMillis(contributor.durationMillis())).append("`\n")
                    .append("   позиции: `")
                    .append(contributor.positions().stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", ")))
                    .append("`\n");
        }
        description.append("\n**Всего:** `").append(summary.totalTracks()).append("` треков • `")
                .append(humanMillis(summary.totalDurationMillis())).append("` • ревизия `")
                .append(snapshot.revision()).append("`.");
        return new EmbedBuilder()
                .setTitle("👥 Кто наполняет очередь")
                .setDescription(description.toString())
                .setColor(Color.CYAN)
                .build();
    }

    public static MessageEmbed queueStats(GuildMusicManager musicManager) {
        TrackScheduler.QueueStats stats = musicManager == null
                ? new TrackScheduler.QueueStats(0L, 0, 0L, 0, 0)
                : musicManager.getScheduler().queueStats();
        return new EmbedBuilder()
                .setTitle("📊 Сводка очереди")
                .setDescription("**Ожидающих треков:** `" + stats.size() + "`\n"
                        + "**Общая длительность:** `" + humanMillis(stats.totalDurationMillis()) + "`\n"
                        + "**Заказчиков:** `" + stats.uniqueRequesters() + "`\n"
                        + "**Дубликатов:** `" + stats.duplicateCount() + "`\n"
                        + "**Ревизия:** `" + stats.revision() + "`\n\n"
                        + "Перед изменением очереди можно передать эту ревизию в `/queue-manage`.")
                .setColor(Color.CYAN)
                .build();
    }

    public static MessageEmbed success(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(Color.CYAN)
                .build();
    }

    public static MessageEmbed error(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(Color.RED)
                .build();
    }

    public static String formatTime(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String formatTrack(AudioTrack track) {
        if (track == null) {
            return "Информация о треке недоступна.";
        }
        return "**" + shorten(track.getInfo().title) + "** — " + shorten(track.getInfo().author)
                + " (`" + formatTime(track.getDuration()) + "`)";
    }

    private static String providerLabel(AudioTrack track) {
        if (track == null || track.getInfo() == null) {
            return MediaProvider.UNKNOWN.label();
        }
        MediaProvider provider = MediaProvider.fromUri(track.getInfo().uri);
        if (provider == MediaProvider.UNKNOWN) {
            provider = MediaProvider.fromIdentifier(track.getInfo().identifier);
        }
        return provider.label();
    }

    private static String storedRequesterLabel(StoredTrack track) {
        return track.requesterUserId() > 0L
                ? "<@" + track.requesterUserId() + ">"
                : shortText(track.requesterDisplayName(), 70);
    }

    private static String requesterLabel(TrackRequester requester) {
        return requester == null ? "Неизвестно" : requester.discordLabel();
    }

    private static String shortText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "Неизвестно";
        }
        return value.length() > maxLength
                ? value.substring(0, Math.max(1, maxLength - 3)) + "..."
                : value;
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "Неизвестно";
        }
        return value.length() > 70 ? value.substring(0, 67) + "..." : value;
    }

    static String progressBar(long positionMillis, long durationMillis, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("Progress width must be positive");
        }
        long safeDuration = Math.max(0L, durationMillis);
        long safePosition = Math.max(0L, Math.min(positionMillis, safeDuration));
        int filled = safeDuration == 0L
                ? 0
                : (int) Math.min(width, Math.round(safePosition * (double) width / safeDuration));
        return "█".repeat(filled) + "░".repeat(width - filled);
    }

    private static long safeDuration(AudioTrack track) {
        return track == null ? 0L : Math.max(0L, track.getDuration());
    }

    private static String humanDuration(Duration duration) {
        return humanMillis(duration.toMillis());
    }

    public static String humanMillis(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format("%d ч %02d мин", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%d мин %02d сек", minutes, seconds);
        }
        return seconds + " сек";
    }
}
