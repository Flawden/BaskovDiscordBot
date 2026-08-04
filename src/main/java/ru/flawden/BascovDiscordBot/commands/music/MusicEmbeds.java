package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;
import ru.flawden.BascovDiscordBot.lavaplayer.VoiceConnectionResult;

import java.awt.Color;
import java.time.Duration;
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
                    .setTitle("▶️ Воспроизведение началось")
                    .setDescription(formatTrack(track)
                            + "\n**Заказал:** " + requester);
            case QUEUED -> embed
                    .setTitle("🎶 Добавлено в очередь")
                    .setDescription(formatTrack(track)
                            + "\n**Заказал:** " + requester
                            + "\n**Позиция:** `" + result.queuePosition() + "`"
                            + "\n**Примерно начнётся через:** `"
                            + humanMillis(result.estimatedWaitMillis()) + "`");
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

    public static MessageEmbed nowPlaying(GuildMusicManager musicManager) {
        AudioPlayer audioPlayer = musicManager == null ? null : musicManager.getAudioPlayer();
        AudioTrack currentTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            return error("🎵 Сейчас тишина", "В данный момент нет воспроизводимых песен.");
        }

        TrackRequest request = musicManager.getScheduler().getCurrentRequest();
        return new EmbedBuilder()
                .setTitle("🎵 Сейчас играет")
                .setColor(Color.CYAN)
                .setDescription("**Название:** `" + shorten(currentTrack.getInfo().title) + "`\n"
                        + "**Автор:** `" + shorten(currentTrack.getInfo().author) + "`\n"
                        + "**Заказал:** " + requesterLabel(request == null ? null : request.requester()) + "\n"
                        + "**Позиция:** `" + formatTime(currentTrack.getPosition()) + " / "
                        + formatTime(currentTrack.getDuration()) + "`\n"
                        + "**Громкость:** `" + audioPlayer.getVolume() + "%`\n"
                        + "**Повтор:** `" + musicManager.getScheduler().getRepeatMode().label() + "`\n"
                        + (audioPlayer.isPaused() ? "⚠️ Воспроизведение на паузе" : "▶️ Воспроизведение активно"))
                .build();
    }

    public static MessageEmbed queue(GuildMusicManager musicManager) {
        AudioPlayer audioPlayer = musicManager == null ? null : musicManager.getAudioPlayer();
        AudioTrack playingTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        List<TrackRequest> requests = musicManager == null
                ? List.of()
                : musicManager.getScheduler().queuedRequests();

        if (playingTrack == null && requests.isEmpty()) {
            return error("🎶 Очередь пуста", "Сейчас ничего не играет. Добавь песню через `/play`.");
        }

        StringBuilder description = new StringBuilder();
        if (playingTrack != null) {
            TrackRequest current = musicManager.getScheduler().getCurrentRequest();
            description.append("**Текущая песня:**\n")
                    .append('`').append(shorten(playingTrack.getInfo().title)).append("` — ")
                    .append(shorten(playingTrack.getInfo().author)).append('\n')
                    .append("**Заказал:** ")
                    .append(requesterLabel(current == null ? null : current.requester())).append('\n')
                    .append("**Позиция:** `")
                    .append(formatTime(playingTrack.getPosition())).append(" / ")
                    .append(formatTime(playingTrack.getDuration())).append('`')
                    .append(audioPlayer.isPaused() ? "\n⚠️ Воспроизведение на паузе" : "")
                    .append("\n\n");
        }

        if (requests.isEmpty()) {
            description.append("**Очередь:**\nСписок следующих песен пуст.");
        } else {
            description.append("**Очередь (").append(requests.size()).append("):**\n");
            for (int index = 0; index < Math.min(10, requests.size()); index++) {
                TrackRequest request = requests.get(index);
                AudioTrack track = request.track();
                description.append(index + 1).append(". `")
                        .append(shorten(track.getInfo().title)).append("` — ")
                        .append(formatTime(track.getDuration()))
                        .append(" • ").append(requesterLabel(request.requester())).append('\n');
            }
            if (requests.size() > 10) {
                description.append("...и ещё ").append(requests.size() - 10).append(" треков.\n");
            }
        }

        if (musicManager != null) {
            description.append("\n\n**Состояние сессии:**\n")
                    .append("Громкость: `").append(audioPlayer.getVolume()).append("%` • ")
                    .append("Повтор: `").append(musicManager.getScheduler().getRepeatMode().label()).append("`\n")
                    .append("До конца текущей очереди: `")
                    .append(humanMillis(musicManager.getScheduler().estimatedWaitMillis())).append('`');
        }

        return new EmbedBuilder()
                .setTitle("🎶 Список треков")
                .setDescription(description.toString())
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

    private static String requesterLabel(TrackRequester requester) {
        return requester == null ? "Неизвестно" : requester.discordLabel();
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "Неизвестно";
        }
        return value.length() > 70 ? value.substring(0, 67) + "..." : value;
    }

    private static String humanDuration(Duration duration) {
        return humanMillis(duration.toMillis());
    }

    private static String humanMillis(long millis) {
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
