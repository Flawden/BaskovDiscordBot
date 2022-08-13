package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class SkipEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).scheduler.nextTrack();
        event.getTextChannel().sendMessage("Воспроизведение следующей песни.").queue();
    }

    @Override
    public String getName() {
        return "skip";
    }

    @Override
    public String helpMessage() {
        return "Пропускает воспроизведенние песни";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
