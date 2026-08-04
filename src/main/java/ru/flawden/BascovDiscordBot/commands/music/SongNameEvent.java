package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.interactions.MusicControls;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class SongNameEvent implements Event {

    private final PlayerManager playerManager;

    public SongNameEvent(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void execute(EventArgs event) {
        GuildMusicManager musicManager = playerManager.findMusicManager(event.getGuild()).orElse(null);

        event.getTextChannel()
                .sendMessageEmbeds(MusicEmbeds.nowPlaying(musicManager))
                .setComponents(MusicControls.rows())
                .queue();
    }

    @Override
    public String getGroup() {
        return "Музыка";
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
