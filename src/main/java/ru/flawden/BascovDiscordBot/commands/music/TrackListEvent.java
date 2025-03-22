package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;
import java.util.Queue;

@Component
public class TrackListEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        var musicManager = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild());
        AudioPlayer audioPlayer = musicManager.scheduler.audioPlayer;
        Queue<AudioTrack> tracks = musicManager.scheduler.queue;
        AudioTrack playingTrack = audioPlayer.getPlayingTrack();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (playingTrack == null && tracks.isEmpty()) {
            embed.setTitle("🎶 Очередь пуста");
            embed.setDescription("Сейчас ничего не играет, и очередь пуста.\n" +
                    "Добавь песню с помощью `!search <название или URL>`!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        StringBuilder description = new StringBuilder();
        if (playingTrack != null) {
            String trackTitle = playingTrack.getInfo().title;
            String trackAuthor = playingTrack.getInfo().author;
            long position = playingTrack.getPosition();
            long duration = playingTrack.getDuration();
            boolean isPaused = audioPlayer.isPaused();

            if (trackTitle.length() > 50) {
                trackTitle = trackTitle.substring(0, 47) + "...";
            }
            if (trackAuthor.length() > 50) {
                trackAuthor = trackAuthor.substring(0, 47) + "...";
            }

            description.append("**Текущая песня:**\n")
                    .append("`").append(trackTitle).append("` — ").append(trackAuthor).append("\n")
                    .append("**Позиция:** `").append(formatTime(position)).append(" / ").append(formatTime(duration)).append("`")
                    .append(isPaused ? "\n⚠️ Воспроизведение на паузе" : "")
                    .append("\n\n");
        }

        if (tracks.isEmpty()) {
            description.append("**Очередь:**\nСписок следующих песен пуст.");
        } else {
            description.append("**Очередь (").append(tracks.size()).append("):**\n");
            int index = 1;
            for (AudioTrack track : tracks) {
                if (index > 10) {
                    description.append("...и ещё ").append(tracks.size() - 10).append(" треков.\n");
                    break;
                }
                String trackTitle = track.getInfo().title;
                String trackAuthor = track.getInfo().author;

                if (trackTitle.length() > 50) {
                    trackTitle = trackTitle.substring(0, 47) + "...";
                }
                if (trackAuthor.length() > 50) {
                    trackAuthor = trackAuthor.substring(0, 47) + "...";
                }

                description.append(index).append(". `").append(trackTitle).append("` — ").append(trackAuthor).append("\n");
                index++;
            }
        }

        embed.setTitle("🎶 Список треков");
        embed.setDescription(description.toString());
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
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
        return "Отображает список песен в очереди";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}