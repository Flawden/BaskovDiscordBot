package ru.flawden.BascovDiscordBot.commands.music;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.interactions.MusicControls;
import ru.flawden.BascovDiscordBot.interactions.RecentSearchHistory;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;
import ru.flawden.BascovDiscordBot.lavaplayer.VoiceConnectionResult;

import java.awt.Color;
import java.time.Duration;

@Slf4j
@Component
public class SearchEvent implements Event {

    private final MediaQueryResolver queryResolver;
    private final MusicControlPolicy controlPolicy;
    private final PlayerManager playerManager;
    private final MusicProperties musicProperties;
    private final RecentSearchHistory searchHistory;

    public SearchEvent(
            MediaQueryResolver queryResolver,
            MusicControlPolicy controlPolicy,
            PlayerManager playerManager,
            MusicProperties musicProperties,
            RecentSearchHistory searchHistory) {
        this.queryResolver = queryResolver;
        this.controlPolicy = controlPolicy;
        this.playerManager = playerManager;
        this.musicProperties = musicProperties;
        this.searchHistory = searchHistory;
    }

    @Override
    public void execute(EventArgs event) {
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);

        if (event.getRawArguments().isBlank()) {
            embed.setTitle("🔍 Ошибка поиска");
            embed.setDescription("Введите название песни, например: `!search Sabaton Heart of Iron`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String query;
        try {
            query = queryResolver.resolve(event.getRawArguments());
        } catch (IllegalArgumentException exception) {
            embed.setTitle("🔒 Ссылка отклонена");
            embed.setDescription(exception.getMessage());
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (!MusicCommandReply.allowOrReply(event, controlPolicy.canStartOrQueue(event))) {
            return;
        }

        var botChannel = event.getSelfVoiceState().getChannel();
        var targetChannel = botChannel != null ? botChannel : event.getMemberVoiceState().getChannel();
        if (targetChannel == null) {
            embed.setTitle("🔍 Голосовой канал потерян");
            embed.setDescription("Похоже, ты вышел из голосового канала. Войди снова и повтори команду.");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        searchHistory.remember(event.getMember().getIdLong(), event.getRawArguments());
        TrackRequester requester =
                new TrackRequester(event.getMember().getIdLong(), event.getMember().getEffectiveName());
        playerManager.ensureVoiceConnection(event.getGuild(), targetChannel)
                .whenComplete((connection, failure) -> {
                    if (failure != null) {
                        log.error("Voice connection future failed in guild {}",
                                event.getGuild().getId(), failure);
                        sendVoiceFailure(event, new VoiceConnectionResult(
                                VoiceConnectionResult.Status.FAILED,
                                "Внутренняя ошибка голосового подключения."));
                        return;
                    }
                    if (!connection.connected()) {
                        sendVoiceFailure(event, connection);
                        return;
                    }

                    log.info("Loading media query in guild {} after voice readiness: type={}",
                            event.getGuild().getId(),
                            query.startsWith("scsearch:") ? "search" : "url");
                    playerManager.loadAndPlay(
                            event.getGuild(),
                            query,
                            requester,
                            result -> {
                                var action = event.getTextChannel()
                                        .sendMessageEmbeds(MusicEmbeds.loadResult(result, musicProperties));
                                if (result.status() == MusicLoadResult.Status.STARTED
                                        || result.status() == MusicLoadResult.Status.QUEUED) {
                                    action.setComponents(MusicControls.rows());
                                }
                                action.queue(
                                        ignored -> { },
                                        sendFailure -> log.warn(
                                                "Failed to send music response to channel {}: {}",
                                                event.getTextChannel().getId(),
                                                sendFailure.getMessage()));
                            });
                });
    }

    private void sendVoiceFailure(EventArgs event, VoiceConnectionResult result) {
        event.getTextChannel()
                .sendMessageEmbeds(MusicEmbeds.voiceConnectionFailure(result))
                .queue(
                        ignored -> { },
                        failure -> log.warn("Failed to send voice failure to channel {}: {}",
                                event.getTextChannel().getId(), failure.getMessage()));
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String helpMessage() {
        return "Ищет песню по названию или принимает ссылку SoundCloud/YouTube";
    }

    @Override
    public boolean needOwner() {
        return false;
    }

    @Override
    public Duration cooldown() {
        return Duration.ofSeconds(3);
    }
}
