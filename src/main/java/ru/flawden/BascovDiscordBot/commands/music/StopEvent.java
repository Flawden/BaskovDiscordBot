package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class StopEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).audioPlayer.stopTrack();
        event.getTextChannel().sendMessage("Воспроизведение успешно остановлено.").queue();
        event.getGuild().getAudioManager().closeAudioConnection();
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String helpMessage() {
        return "Останавливает воспроизведение всех песен";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
