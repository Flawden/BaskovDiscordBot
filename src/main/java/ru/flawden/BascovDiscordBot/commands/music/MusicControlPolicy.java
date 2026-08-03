package ru.flawden.BascovDiscordBot.commands.music;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

/**
 * Единые правила доступа к управлению музыкальной сессией.
 */
@Component
public class MusicControlPolicy {

    public Decision canStartOrQueue(EventArgs event) {
        return decide(
                Mode.START_OR_QUEUE,
                isPrivileged(event),
                channelId(event.getMemberVoiceState()),
                channelId(event.getSelfVoiceState()));
    }

    public Decision canControlPlayback(EventArgs event) {
        return decide(
                Mode.CONTROL_PLAYBACK,
                isPrivileged(event),
                channelId(event.getMemberVoiceState()),
                channelId(event.getSelfVoiceState()));
    }

    static Decision decide(Mode mode, boolean privileged, Long memberChannelId, Long botChannelId) {
        if (mode == Mode.START_OR_QUEUE && botChannelId == null) {
            if (memberChannelId == null) {
                return Decision.denied("Сначала войди в голосовой канал, чтобы я понимал, куда подключаться.");
            }
            return Decision.granted();
        }

        if (botChannelId == null) {
            return Decision.denied("Я сейчас не подключён к голосовому каналу.");
        }

        if (privileged) {
            return Decision.granted();
        }

        if (memberChannelId == null) {
            return Decision.denied("Для управления музыкой войди в тот же голосовой канал, где нахожусь я.");
        }

        if (!botChannelId.equals(memberChannelId)) {
            return Decision.denied("Музыкой могут управлять участники моего голосового канала. Администратор сервера может управлять из любого канала.");
        }

        return Decision.granted();
    }

    private static boolean isPrivileged(EventArgs event) {
        return event.getMember().isOwner() || event.getMember().hasPermission(Permission.MANAGE_SERVER);
    }

    private static Long channelId(GuildVoiceState state) {
        if (state == null || !state.inAudioChannel() || state.getChannel() == null) {
            return null;
        }
        return state.getChannel().getIdLong();
    }

    enum Mode {
        START_OR_QUEUE,
        CONTROL_PLAYBACK
    }

    public record Decision(boolean allowed, String message) {
        static Decision granted() {
            return new Decision(true, "");
        }

        static Decision denied(String message) {
            return new Decision(false, message);
        }
    }
}
