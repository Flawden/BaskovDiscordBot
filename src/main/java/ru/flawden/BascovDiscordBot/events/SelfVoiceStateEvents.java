package ru.flawden.BascovDiscordBot.events;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnostics;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

/**
 * Наблюдает за voice-переходами самого бота и за возвращением людей в канал,
 * для которого остался pending checkpoint.
 */
@Slf4j
@Component
public class SelfVoiceStateEvents extends ListenerAdapter {

    private final VoiceDiagnostics diagnostics;
    private final PlayerManager playerManager;

    public SelfVoiceStateEvents(
            VoiceDiagnostics diagnostics,
            PlayerManager playerManager) {
        this.diagnostics = diagnostics;
        this.playerManager = playerManager;
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getMember().getIdLong() != event.getGuild().getSelfMember().getIdLong()) {
            var joinedByListener = event.getChannelJoined();
            if (joinedByListener != null && !event.getMember().getUser().isBot()) {
                playerManager.handleHumanVoiceJoin(event.getGuild(), joinedByListener.getIdLong());
            }
            return;
        }

        var joined = event.getChannelJoined();
        var left = event.getChannelLeft();
        String transition;
        Long channelId;
        if (joined != null && left != null) {
            transition = "MOVE";
            channelId = joined.getIdLong();
        } else if (joined != null) {
            transition = "JOIN";
            channelId = joined.getIdLong();
        } else {
            transition = "LEAVE";
            channelId = left == null ? null : left.getIdLong();
        }

        diagnostics.selfVoiceEvent(event.getGuild().getIdLong(), transition, channelId);
        playerManager.handleSelfVoiceTransition(event.getGuild(), transition, channelId);
        log.warn("Self voice state changed: guild={}, transition={}, channel={}",
                event.getGuild().getId(), transition, channelId == null ? "none" : channelId);
    }
}
