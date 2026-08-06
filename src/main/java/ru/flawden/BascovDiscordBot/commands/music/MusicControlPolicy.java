package ru.flawden.BascovDiscordBot.commands.music;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;
import ru.flawden.BascovDiscordBot.settings.PlaybackAccessMode;

/**
 * Единые правила доступа к управлению музыкальной сессией.
 */
@Component
public class MusicControlPolicy {

    private final GuildPreferencesRepository preferencesRepository;

    public MusicControlPolicy(GuildPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    public Decision canStartOrQueue(EventArgs event) {
        return canStartOrQueue(event.getMember(), event.getMemberVoiceState(), event.getSelfVoiceState());
    }

    public Decision canControlPlayback(EventArgs event) {
        return canControlPlayback(event.getMember(), event.getMemberVoiceState(), event.getSelfVoiceState());
    }

    public Decision canStartOrQueue(
            Member member,
            GuildVoiceState memberVoiceState,
            GuildVoiceState botVoiceState) {
        return decide(
                Mode.START_OR_QUEUE,
                isAdministrator(member),
                channelId(memberVoiceState),
                channelId(botVoiceState));
    }

    public Decision canControlPlayback(
            Member member,
            GuildVoiceState memberVoiceState,
            GuildVoiceState botVoiceState) {
        GuildPreferences preferences = preferences(member);
        return controlDecision(
                preferences.accessMode(),
                isAdministrator(member),
                hasDjRole(member, preferences),
                channelId(memberVoiceState),
                channelId(botVoiceState));
    }

    public SkipDecision canSkip(
            Member member,
            GuildVoiceState memberVoiceState,
            GuildVoiceState botVoiceState) {
        GuildPreferences preferences = preferences(member);
        return skipDecision(
                preferences.accessMode(),
                isAdministrator(member),
                hasDjRole(member, preferences),
                channelId(memberVoiceState),
                channelId(botVoiceState));
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
            return Decision.denied("Музыкой могут управлять участники моего голосового канала. "
                    + "Администратор сервера может управлять из любого канала.");
        }

        return Decision.granted();
    }

    static Decision controlDecision(
            PlaybackAccessMode accessMode,
            boolean administrator,
            boolean dj,
            Long memberChannelId,
            Long botChannelId) {
        Decision channelDecision = decide(
                Mode.CONTROL_PLAYBACK,
                administrator,
                memberChannelId,
                botChannelId);
        if (!channelDecision.allowed() || administrator || accessMode == PlaybackAccessMode.OPEN) {
            return channelDecision;
        }
        if (dj && memberChannelId != null && memberChannelId.equals(botChannelId)) {
            return Decision.granted();
        }
        if (accessMode == PlaybackAccessMode.VOTE_SKIP) {
            return Decision.denied("Прямое управление доступно DJ. Для пропуска используй `/voteskip` "
                    + "или кнопку `Следующий` под `/now`.");
        }
        return Decision.denied("Прямое управление доступно владельцу сервера, участникам с `Manage Server` "
                + "или настроенной DJ-роли.");
    }

    static SkipDecision skipDecision(
            PlaybackAccessMode accessMode,
            boolean administrator,
            boolean dj,
            Long memberChannelId,
            Long botChannelId) {
        Decision channelDecision = decide(
                Mode.CONTROL_PLAYBACK,
                administrator,
                memberChannelId,
                botChannelId);
        if (!channelDecision.allowed()) {
            return SkipDecision.denied(channelDecision.message());
        }
        if (administrator || accessMode == PlaybackAccessMode.OPEN) {
            return SkipDecision.direct();
        }
        if (dj && memberChannelId != null && memberChannelId.equals(botChannelId)) {
            return SkipDecision.direct();
        }
        if (accessMode == PlaybackAccessMode.VOTE_SKIP) {
            return SkipDecision.vote();
        }
        return SkipDecision.denied("Пропуск доступен владельцу сервера, участникам с `Manage Server` "
                + "или настроенной DJ-роли.");
    }

    private GuildPreferences preferences(Member member) {
        if (member == null || member.getGuild() == null) {
            return new GuildPreferences(100, RepeatMode.OFF);
        }
        return preferencesRepository.get(member.getGuild().getIdLong());
    }

    private static boolean isAdministrator(Member member) {
        return member != null && (member.isOwner() || member.hasPermission(Permission.MANAGE_SERVER));
    }

    private static boolean hasDjRole(Member member, GuildPreferences preferences) {
        if (member == null || preferences == null || !preferences.hasDjRole()) {
            return false;
        }
        long configuredRoleId = preferences.djRoleId();
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == configuredRoleId);
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

    public enum SkipAccess {
        DIRECT,
        VOTE,
        DENIED
    }

    public record Decision(boolean allowed, String message) {
        static Decision granted() {
            return new Decision(true, "");
        }

        static Decision denied(String message) {
            return new Decision(false, message);
        }
    }

    public record SkipDecision(SkipAccess access, String message) {
        static SkipDecision direct() {
            return new SkipDecision(SkipAccess.DIRECT, "");
        }

        static SkipDecision vote() {
            return new SkipDecision(SkipAccess.VOTE, "");
        }

        static SkipDecision denied(String message) {
            return new SkipDecision(SkipAccess.DENIED, message);
        }
    }
}
