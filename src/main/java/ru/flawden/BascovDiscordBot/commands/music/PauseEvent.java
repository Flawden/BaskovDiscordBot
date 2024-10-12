package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class PauseEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).audioPlayer.setPaused(true);
        event.getTextChannel().sendMessage("Воспроизведение успешно приостановлено.").queue();
    }

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public String helpMessage() {
        return "Приостанавливает воспроизведение песни";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
