package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class SongNameEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        AudioTrack nameOfSong = PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).scheduler.audioPlayer.getPlayingTrack();
        if (nameOfSong != null) {
            event.getTextChannel().sendMessage("Название текущей песни: " + nameOfSong.getInfo().title).queue();
        } else {
            event.getTextChannel().sendMessage("В данный момент нет воспроизводимых песен.").queue();
        }

    }

    @Override
    public String getName() {
        return "SongName";
    }

    @Override
    public String helpMessage() {
        return "Отображает название проигрываемой композиции";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
