package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.util.Queue;

@Component
public class TrackListEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        Queue<AudioTrack> tracks = PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).scheduler.queue;
        AudioTrack playingTrack = PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).scheduler.audioPlayer.getPlayingTrack();
        if(tracks.isEmpty()) {
            StringBuilder message = new StringBuilder();
            if(playingTrack != null) {
                message.append("Текущая песня: " + playingTrack.getInfo().title);
            }
            message.append("\nСписок следующих песен пуст.\n");
            event.getTextChannel().sendMessage(message).queue();
            return;
        }
        StringBuilder info = new StringBuilder();
        if (playingTrack != null) {
            info.append("Текущая песня: " + playingTrack.getInfo().title + "\n\n");
        }
        info.append("Следующие песни: \n");
        for(AudioTrack track: tracks) {
            info.append(track.getInfo().title).append("\n");
        }
        event.getTextChannel().sendMessage(info).queue();
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
