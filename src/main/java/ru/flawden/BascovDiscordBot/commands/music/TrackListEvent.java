package ru.flawden.BascovDiscordBot.commands.music;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.interactions.MusicControls;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

@Component
public class TrackListEvent implements Event {

    private final PlayerManager playerManager;

    public TrackListEvent(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void execute(EventArgs event) {
        GuildMusicManager musicManager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        MusicEmbeds.QueueView view = MusicEmbeds.queueView(musicManager, 1);
        event.getTextChannel()
                .sendMessageEmbeds(view.embed())
                .setComponents(MusicControls.queueRows(view.page(), view.totalPages()))
                .queue();
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
