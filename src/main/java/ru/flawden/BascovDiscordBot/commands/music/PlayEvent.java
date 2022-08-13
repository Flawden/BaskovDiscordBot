package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class PlayEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        if (event.getArgs().length <= 1) {
            PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).audioPlayer.setPaused(false);
            event.getTextChannel().sendMessage("Воспроизведение успешно возобновлено.").queue();
            return;
        }
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String helpMessage() {
        return "Продолжает воспроизведение песни";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
