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
        StringBuilder info = new StringBuilder();
        for(AudioTrack track: tracks) {
            info.append(track.getInfo().title).append("\n");
        }
        event.getTextChannel().sendMessage("Список песен: \n" + info).queue();
    }

    @Override
    public String getName() {
        return "TrackList";
    }

    @Override
    public String helpMessage() {
        return "Отображает список песен в очереди.";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
