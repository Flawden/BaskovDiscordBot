package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.Color;
import java.util.List;

@Component
public class TrackListEvent implements Event {

    private final PlayerManager playerManager;

    public TrackListEvent(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void execute(EventArgs event) {
        GuildMusicManager musicManager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        AudioPlayer audioPlayer = musicManager == null ? null : musicManager.getAudioPlayer();
        AudioTrack playingTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        List<AudioTrack> tracks = musicManager == null
                ? List.of()
                : musicManager.getScheduler().queuedTracks();
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);

        if (playingTrack == null && tracks.isEmpty()) {
            embed.setTitle("🎶 Очередь пуста");
            embed.setDescription("Сейчас ничего не играет. Добавь песню через `!search <название или URL>`.");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
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

        embed.setTitle("🎶 Список треков");
        embed.setDescription(description.toString());
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private static String shorten(String value) {
        return value.length() > 50 ? value.substring(0, 47) + "..." : value;
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "TrackList";
    }

    @Override
    public String helpMessage() {
        return "Отображает текущий трек и очередь";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
