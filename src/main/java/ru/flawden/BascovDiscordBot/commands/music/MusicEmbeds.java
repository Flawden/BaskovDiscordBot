package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicLoadResult;

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

        switch (result.status()) {
            case STARTED -> embed
                    .setTitle("▶️ Воспроизведение началось")
                    .setDescription(formatTrack(track));
            case QUEUED -> embed
                    .setTitle("🎶 Добавлено в очередь")
                    .setDescription(formatTrack(track) + "\n**Позиция:** `" + result.queuePosition() + "`");
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

    public static MessageEmbed nowPlaying(AudioPlayer audioPlayer) {
        AudioTrack currentTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            return error("🎵 Сейчас тишина", "В данный момент нет воспроизводимых песен.");
        }

        return new EmbedBuilder()
                .setTitle("🎵 Сейчас играет")
                .setColor(Color.CYAN)
                .setDescription("**Название:** `" + shorten(currentTrack.getInfo().title) + "`\n"
                        + "**Автор:** `" + shorten(currentTrack.getInfo().author) + "`\n"
                        + "**Позиция:** `" + formatTime(currentTrack.getPosition()) + " / "
                        + formatTime(currentTrack.getDuration()) + "`\n"
                        + (audioPlayer.isPaused() ? "⚠️ Воспроизведение на паузе" : "▶️ Воспроизведение активно"))
                .build();
    }

    public static MessageEmbed queue(GuildMusicManager musicManager) {
        AudioPlayer audioPlayer = musicManager == null ? null : musicManager.getAudioPlayer();
        AudioTrack playingTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        List<AudioTrack> tracks = musicManager == null
                ? List.of()
                : musicManager.getScheduler().queuedTracks();

        if (playingTrack == null && tracks.isEmpty()) {
            return error("🎶 Очередь пуста", "Сейчас ничего не играет. Добавь песню через `/play`.");
        }

        StringBuilder description = new StringBuilder();
        if (playingTrack != null) {
            description.append("**Текущая песня:**\n")
                    .append('`').append(shorten(playingTrack.getInfo().title)).append("` — ")
                    .append(shorten(playingTrack.getInfo().author)).append('\n')
                    .append("**Позиция:** `")
                    .append(formatTime(playingTrack.getPosition())).append(" / ")
                    .append(formatTime(playingTrack.getDuration())).append('`')
                    .append(audioPlayer.isPaused() ? "\n⚠️ Воспроизведение на паузе" : "")
                    .append("\n\n");
        }

        if (tracks.isEmpty()) {
            description.append("**Очередь:**\nСписок следующих песен пуст.");
        } else {
            description.append("**Очередь (").append(tracks.size()).append("):**\n");
            for (int index = 0; index < Math.min(10, tracks.size()); index++) {
                AudioTrack track = tracks.get(index);
                description.append(index + 1).append(". `")
                        .append(shorten(track.getInfo().title)).append("` — ")
                        .append(shorten(track.getInfo().author)).append('\n');
            }
            if (tracks.size() > 10) {
                description.append("...и ещё ").append(tracks.size() - 10).append(" треков.\n");
            }
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
        return "**" + shorten(track.getInfo().title) + "** — " + shorten(track.getInfo().author);
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "Неизвестно";
        }
        return value.length() > 70 ? value.substring(0, 67) + "..." : value;
    }

    private static String humanDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours == 0) {
            return minutes + " мин";
        }
        if (remainingMinutes == 0) {
            return hours + " ч";
        }
        return hours + " ч " + remainingMinutes + " мин";
    }
}
