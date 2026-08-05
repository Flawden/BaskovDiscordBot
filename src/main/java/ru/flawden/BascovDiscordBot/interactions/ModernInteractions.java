package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.commands.VersionEvent;
import ru.flawden.BascovDiscordBot.commands.music.MediaQueryResolver;
import ru.flawden.BascovDiscordBot.commands.music.MusicControlPolicy;
import ru.flawden.BascovDiscordBot.commands.music.MusicEmbeds;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.dave.DaveRuntimeInfo;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlaybackReadinessResult;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackScheduler;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;
import ru.flawden.BascovDiscordBot.lavaplayer.VoiceConnectionResult;
import ru.flawden.BascovDiscordBot.operations.JdaRuntimeInfo;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Slash-команды, autocomplete и component buttons.
 */
@Slf4j
@Component
public class ModernInteractions extends ListenerAdapter {

    private final PlayerManager playerManager;
    private final MusicControlPolicy controlPolicy;
    private final MediaQueryResolver queryResolver;
    private final MusicProperties musicProperties;
    private final RecentSearchHistory searchHistory;
    private final VersionEvent versionEvent;
    private final GuildPreferencesRepository preferencesRepository;
    private final OperationalMetrics operationalMetrics;
    private final RuntimeHealthMonitor healthMonitor;
    private final DaveRuntimeInfo daveRuntimeInfo;

    public ModernInteractions(
            PlayerManager playerManager,
            MusicControlPolicy controlPolicy,
            MediaQueryResolver queryResolver,
            MusicProperties musicProperties,
            RecentSearchHistory searchHistory,
            VersionEvent versionEvent,
            GuildPreferencesRepository preferencesRepository,
            OperationalMetrics operationalMetrics,
            RuntimeHealthMonitor healthMonitor,
            DaveRuntimeInfo daveRuntimeInfo) {
        this.playerManager = playerManager;
        this.controlPolicy = controlPolicy;
        this.queryResolver = queryResolver;
        this.musicProperties = musicProperties;
        this.searchHistory = searchHistory;
        this.versionEvent = versionEvent;
        this.preferencesRepository = preferencesRepository;
        this.operationalMetrics = operationalMetrics;
        this.healthMonitor = healthMonitor;
        this.daveRuntimeInfo = daveRuntimeInfo;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🏠 Нужен сервер",
                            "Музыкальные команды доступны только внутри Discord-сервера."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            switch (event.getName()) {
                case "help" -> event.replyEmbeds(helpEmbed()).setEphemeral(true).queue();
                case "version" -> event.replyEmbeds(versionEvent.buildEmbed()).setEphemeral(true).queue();
                case "status" -> status(event);
                case "play" -> play(event);
                case "pause" -> pause(event, true);
                case "resume" -> pause(event, false);
                case "previous" -> previous(event);
                case "skip" -> skip(event);
                case "stop" -> stop(event);
                case "queue" -> queue(event);
                case "now" -> now(event);
                case "seek" -> seek(event);
                case "volume" -> volume(event);
                case "repeat" -> repeat(event);
                case "shuffle" -> shuffle(event);
                case "remove" -> remove(event);
                case "move" -> move(event);
                case "clear" -> clear(event);
                case "settings" -> settings(event);
                default -> event.replyEmbeds(MusicEmbeds.error(
                                "❌ Неизвестная slash-команда",
                                "Обнови список команд Discord или используй `/help`."))
                        .setEphemeral(true)
                        .queue();
            }
            operationalMetrics.recordSuccess(OperationalMetrics.Channel.SLASH);
        } catch (RuntimeException exception) {
            operationalMetrics.recordFailure(OperationalMetrics.Channel.SLASH);
            log.error("Slash command '{}' failed in guild {} for user {}",
                    event.getName(), event.getGuild().getId(), event.getUser().getId(), exception);
            if (!event.isAcknowledged()) {
                event.replyEmbeds(MusicEmbeds.error(
                                "💥 Команда упала",
                                "Произошла внутренняя ошибка. Попробуй ещё раз чуть позже."))
                        .setEphemeral(true)
                        .queue();
            } else {
                event.getHook().sendMessageEmbeds(MusicEmbeds.error(
                                "💥 Команда упала",
                                "Произошла внутренняя ошибка. Попробуй ещё раз чуть позже."))
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!"play".equals(event.getName()) || !"query".equals(event.getFocusedOption().getName())) {
            return;
        }

        List<Command.Choice> choices = searchHistory
                .suggest(event.getUser().getIdLong(), event.getFocusedOption().getValue())
                .stream()
                .map(query -> new Command.Choice(query, query))
                .toList();
        event.replyChoices(choices).queue(
                ignored -> { },
                exception -> {
                    if (isExpiredAutocomplete(exception)) {
                        log.debug("Autocomplete interaction expired before reply: user={}, query={}",
                                event.getUser().getId(),
                                event.getFocusedOption().getValue());
                        return;
                    }
                    log.warn("Autocomplete reply failed: user={}, query={}",
                            event.getUser().getId(),
                            event.getFocusedOption().getValue(),
                            exception);
                });
    }

    private static boolean isExpiredAutocomplete(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("10062") || message.contains("Unknown interaction"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!MusicControls.supports(event.getComponentId())) {
            return;
        }
        try {
            handleMusicButton(event);
            operationalMetrics.recordSuccess(OperationalMetrics.Channel.BUTTON);
        } catch (RuntimeException exception) {
            operationalMetrics.recordFailure(OperationalMetrics.Channel.BUTTON);
            log.error("Music button '{}' failed for user {}",
                    event.getComponentId(), event.getUser().getId(), exception);
            if (!event.isAcknowledged()) {
                event.replyEmbeds(MusicEmbeds.error(
                                "💥 Кнопка не сработала",
                                "Произошла внутренняя ошибка. Попробуй ещё раз чуть позже."))
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    private void handleMusicButton(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.replyEmbeds(MusicEmbeds.error("🏠 Нужен сервер", "Эта кнопка работает только на сервере."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (MusicControls.QUEUE.equals(event.getComponentId())) {
            GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
            MusicEmbeds.QueueView view = MusicEmbeds.queueView(manager, 1);
            event.replyEmbeds(view.embed())
                    .setComponents(MusicControls.queueRows(view.page(), view.totalPages()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        OptionalInt queuePage = MusicControls.queuePage(event.getComponentId());
        if (queuePage.isPresent()) {
            GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
            MusicEmbeds.QueueView view = MusicEmbeds.queueView(
                    manager,
                    queuePage.getAsInt());
            event.editMessageEmbeds(view.embed())
                    .setComponents(MusicControls.queueRows(view.page(), view.totalPages()))
                    .queue();
            return;
        }

        MusicControlPolicy.Decision decision = controlDecision(guild, member);
        if (!decision.allowed()) {
            event.replyEmbeds(MusicEmbeds.error("🎧 Управление недоступно", decision.message()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (event.getComponentId()) {
            case MusicControls.TOGGLE -> toggleFromButton(event, guild);
            case MusicControls.PREVIOUS -> previousFromButton(event, guild);
            case MusicControls.SEEK_BACKWARD -> seekFromButton(event, guild, -15_000L);
            case MusicControls.SEEK_FORWARD -> seekFromButton(event, guild, 15_000L);
            case MusicControls.SKIP -> skipFromButton(event, guild);
            case MusicControls.SHUFFLE -> shuffleFromButton(event, guild);
            case MusicControls.STOP -> stopFromButton(event, guild);
            case MusicControls.REPEAT -> repeatFromButton(event, guild);
            default -> { }
        }
    }

    private void status(SlashCommandInteractionEvent event) {
        RuntimeHealthMonitor.Snapshot runtime = healthMonitor.snapshot();
        OperationalMetrics.Snapshot commands = operationalMetrics.snapshot();
        MusicRuntimeSnapshot music = playerManager.runtimeSnapshot();
        VoiceDiagnosticSnapshot voice = playerManager.voiceDiagnosticsSnapshot(event.getGuild());

        String discord = StatusMessageFormatter.discord(runtime, JdaRuntimeInfo.version());
        String daveState = StatusMessageFormatter.dave(daveRuntimeInfo.snapshot());
        String musicState = StatusMessageFormatter.music(music);
        String playbackState = StatusMessageFormatter.playback(
                playerManager.findMusicManager(event.getGuild()).orElse(null));
        String voiceState = StatusMessageFormatter.voice(voice);
        String voiceHistory = StatusMessageFormatter.voiceHistory(voice);
        String commandState = StatusMessageFormatter.commands(commands);

        event.replyEmbeds(new EmbedBuilder()
                        .setTitle("🩺 Состояние Baskov Discord Bot")
                        .setDescription("Uptime: `" + formatDuration(commands.uptime()) + "`")
                        .setColor("CONNECTED".equals(runtime.jdaStatus()) ? Color.GREEN : Color.ORANGE)
                        .addField("Discord gateway", discord, true)
                        .addField("DAVE / E2EE", daveState, true)
                        .addField("Музыка", musicState, true)
                        .addField("Playback modes", playbackState, true)
                        .addField("Voice transport", voiceState, true)
                        .addField("Voice history", voiceHistory, false)
                        .addField("Команды с запуска", commandState, false)
                        .setFooter("Health heartbeat обновляется каждые 10 секунд")
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private void play(SlashCommandInteractionEvent event) {
        String rawQuery = event.getOption("query", "", OptionMapping::getAsString).trim();
        String identifier;
        try {
            identifier = queryResolver.resolve(rawQuery);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error("🔒 Запрос отклонён", exception.getMessage()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        MusicControlPolicy.Decision decision = controlPolicy.canStartOrQueue(
                member,
                member.getVoiceState(),
                guild.getSelfMember().getVoiceState());
        if (!decision.allowed()) {
            event.replyEmbeds(MusicEmbeds.error("🎧 Управление недоступно", decision.message()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        var botChannel = guild.getSelfMember().getVoiceState().getChannel();
        var targetChannel = botChannel != null ? botChannel : member.getVoiceState().getChannel();
        if (targetChannel == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔍 Голосовой канал потерян",
                            "Похоже, ты вышел из голосового канала. Войди снова и повтори команду."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        searchHistory.remember(event.getUser().getIdLong(), rawQuery);
        TrackRequester requester =
                new TrackRequester(event.getUser().getIdLong(), member.getEffectiveName());
        event.deferReply().queue(hook -> playerManager
                .ensureVoiceConnection(guild, targetChannel)
                .whenComplete((connection, failure) -> {
                    if (failure != null) {
                        log.error("Voice connection future failed in guild {}", guild.getId(), failure);
                        editVoiceFailure(hook, new VoiceConnectionResult(
                                VoiceConnectionResult.Status.FAILED,
                                "Внутренняя ошибка голосового подключения."));
                        return;
                    }
                    if (!connection.connected()) {
                        editVoiceFailure(hook, connection);
                        return;
                    }
                    playerManager.loadAndPlay(
                            guild,
                            identifier,
                            requester,
                            result -> editLoadResult(hook, guild, result));
                }));
    }

    private void editVoiceFailure(InteractionHook hook, VoiceConnectionResult result) {
        hook.editOriginalEmbeds(MusicEmbeds.voiceConnectionFailure(result)).queue();
    }

    private void editLoadResult(InteractionHook hook, Guild guild, MusicLoadResult result) {
        var action = hook.editOriginalEmbeds(MusicEmbeds.loadResult(result, musicProperties));
        if (result.status() == MusicLoadResult.Status.STARTED
                || result.status() == MusicLoadResult.Status.QUEUED) {
            action.setComponents(MusicControls.rows());
        }
        action.queue();

        if (result.status() != MusicLoadResult.Status.STARTED || result.track() == null) {
            return;
        }

        playerManager.awaitPlaybackReady(guild, result.track())
                .whenComplete((readiness, failure) -> {
                    if (failure != null) {
                        log.error("Playback readiness future failed in guild {}", guild.getId(), failure);
                        hook.editOriginalEmbeds(MusicEmbeds.error(
                                        "❌ Не удалось подтвердить воспроизведение",
                                        "Внутренняя ошибка проверки Discord media transport."))
                                .setComponents(java.util.List.of())
                                .queue();
                        playerManager.stopAndRelease(guild);
                        return;
                    }
                    if (readiness.ready()) {
                        hook.editOriginalEmbeds(MusicEmbeds.playbackConfirmed(result))
                                .setComponents(MusicControls.rows())
                                .queue();
                        return;
                    }
                    hook.editOriginalEmbeds(MusicEmbeds.playbackReadinessFailure(
                                    readiness,
                                    JdaRuntimeInfo.version()))
                            .setComponents(java.util.List.of())
                            .queue();
                    if (readiness.status() != PlaybackReadinessResult.Status.SESSION_CLOSED) {
                        playerManager.stopAndRelease(guild);
                    }
                });
    }

    private void pause(SlashCommandInteractionEvent event, boolean paused) {
        if (!allowControl(event)) {
            return;
        }
        AudioPlayer player = currentPlayer(event.getGuild());
        if (player == null || player.getPlayingTrack() == null) {
            event.replyEmbeds(MusicEmbeds.error("🎵 Сейчас тишина", "Сейчас нечего переключать."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        player.setPaused(paused);
        String title = paused ? "⏸️ Воспроизведение приостановлено" : "▶️ Воспроизведение продолжено";
        event.replyEmbeds(MusicEmbeds.success(
                        title,
                        "Текущая песня: `" + player.getPlayingTrack().getInfo().title + "`"))
                .setComponents(MusicControls.rows())
                .queue();
    }

    private void previous(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        TrackScheduler.PreviousResult result = manager.getScheduler().previousTrack();
        if (result.status() == TrackScheduler.PreviousStatus.NO_HISTORY) {
            event.replyEmbeds(MusicEmbeds.error(
                            "⏮️ Предыдущего трека нет",
                            "История текущей музыкальной сессии пока пуста."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (result.status() == TrackScheduler.PreviousStatus.QUEUE_CAPACITY_EXCEEDED) {
            event.replyEmbeds(MusicEmbeds.error(
                            "⏮️ Не удалось вернуться",
                            "Очередь и история достигли безопасного лимита. Очисти несколько позиций и повтори команду."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.replyEmbeds(MusicEmbeds.success(
                        "⏮️ Возвращаю предыдущий трек",
                        "Сейчас играет: `" + result.request().track().getInfo().title + "`."
                                + (result.returnedCurrentToQueue()
                                ? "\nПрерванный трек поставлен первым в очередь."
                                : "")))
                .setComponents(MusicControls.nowRows())
                .queue();
    }

    private void skip(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        AudioTrack current = manager == null ? null : manager.getAudioPlayer().getPlayingTrack();
        if (current == null) {
            event.replyEmbeds(MusicEmbeds.error("⏭️ Нечего пропускать", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackRequest next = manager.getScheduler().nextTrack();
        String description = next == null
                ? "Песня `" + current.getInfo().title + "` пропущена. Очередь пуста."
                : "Песня `" + current.getInfo().title + "` пропущена.\nСейчас играет: `"
                + next.track().getInfo().title + "`";
        event.replyEmbeds(MusicEmbeds.success("⏭️ Песня пропущена", description))
                .setComponents(MusicControls.rows())
                .queue();
    }

    private void stop(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        Guild guild = event.getGuild();
        AudioPlayer player = currentPlayer(guild);
        if (player == null || player.getPlayingTrack() == null) {
            event.replyEmbeds(MusicEmbeds.error("⏹️ Уже остановлено", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String title = player.getPlayingTrack().getInfo().title;
        playerManager.stopAndRelease(guild);
        event.replyEmbeds(MusicEmbeds.success(
                "⏹️ Воспроизведение остановлено",
                "Песня `" + title + "` остановлена, очередь очищена, бот отключён."))
                .queue();
    }

    private void queue(SlashCommandInteractionEvent event) {
        long requestedPage = event.getOption("page", 1L, OptionMapping::getAsLong);
        if (requestedPage < 1L || requestedPage > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📄 Неверная страница",
                            "Номер страницы должен быть положительным целым числом."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        MusicEmbeds.QueueView view = MusicEmbeds.queueView(
                manager,
                Math.toIntExact(requestedPage));
        event.replyEmbeds(view.embed())
                .setComponents(MusicControls.queueRows(view.page(), view.totalPages()))
                .queue();
    }

    private void now(SlashCommandInteractionEvent event) {
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        event.replyEmbeds(MusicEmbeds.nowPlaying(manager))
                .setComponents(MusicControls.nowRows())
                .queue();
    }

    private void seek(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        String input = event.getOption("position", "", OptionMapping::getAsString);
        OptionalLong parsed = PlaybackPositionParser.parseMillis(input);
        if (parsed.isEmpty()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "⏳ Неверная позиция",
                            "Используй `SS`, `MM:SS` или `HH:MM:SS`, например `01:30`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        AudioPlayer player = currentPlayer(event.getGuild());
        AudioTrack track = player == null ? null : player.getPlayingTrack();
        if (track == null) {
            event.replyEmbeds(MusicEmbeds.error("🎵 Сейчас тишина", "Перематывать нечего."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!track.isSeekable()) {
            event.replyEmbeds(MusicEmbeds.error("⏳ Перемотка недоступна", "Этот источник нельзя перематывать."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        long position = parsed.getAsLong();
        if (position > track.getDuration()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "⏳ Позиция за пределами трека",
                            "Длительность трека: `" + MusicEmbeds.formatTime(track.getDuration()) + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        applySeek(event, track, position);
    }

    private void applySeek(
            SlashCommandInteractionEvent event,
            AudioTrack track,
            long requestedPosition) {
        long clamped = Math.max(0L, Math.min(requestedPosition, track.getDuration()));
        track.setPosition(clamped);
        event.replyEmbeds(MusicEmbeds.success(
                        "⏳ Трек перемотан",
                        "Новая позиция: `" + MusicEmbeds.formatTime(clamped) + "`."))
                .setComponents(MusicControls.nowRows())
                .queue();
    }

    private void volume(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        long requested = event.getOption("level", -1L, OptionMapping::getAsLong);
        if (requested < 0 || requested > musicProperties.getMaxVolume()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔊 Недопустимая громкость",
                            "Укажи значение от `0` до `" + musicProperties.getMaxVolume() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int volume = Math.toIntExact(requested);
        manager.getAudioPlayer().setVolume(volume);
        manager.markActivity();
        event.replyEmbeds(MusicEmbeds.success(
                        "🔊 Громкость изменена",
                        "Новая громкость текущей сессии: `" + requested + "%`."))
                .setComponents(MusicControls.rows())
                .queue();
    }

    private void repeat(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        String rawMode = event.getOption("mode", "off", OptionMapping::getAsString);
        RepeatMode mode;
        try {
            mode = RepeatMode.parse(rawMode);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔁 Неизвестный режим",
                            "Доступны режимы: `off`, `track` и `queue`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        manager.getScheduler().setRepeatMode(mode);
        event.replyEmbeds(MusicEmbeds.success(
                        "🔁 Режим повтора изменён",
                        "Текущий режим сессии: `" + mode.label() + "`."))
                .setComponents(MusicControls.rows())
                .queue();
    }

    private void shuffle(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        int size = manager.getScheduler().shuffleQueue();
        if (size < 2) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔀 Перемешивать нечего",
                            "В очереди должно быть хотя бы два ожидающих трека."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "🔀 Очередь перемешана",
                        "Новый порядок применён к `" + size + "` трекам."))
                .setComponents(MusicControls.rows())
                .queue();
    }

    private void remove(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        int position = Math.toIntExact(event.getOption("position", -1L, OptionMapping::getAsLong));
        TrackRequest removed = manager.getScheduler().removeAt(position);
        if (removed == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🗑️ Позиция не найдена",
                            "Сейчас в очереди `" + manager.getScheduler().queueSize() + "` треков."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "🗑️ Трек удалён",
                        "Удалён: `" + removed.track().getInfo().title + "`\nЗаказывал: "
                                + removed.requester().discordLabel()))
                .queue();
    }

    private void move(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        int from = Math.toIntExact(event.getOption("from", -1L, OptionMapping::getAsLong));
        int to = Math.toIntExact(event.getOption("to", -1L, OptionMapping::getAsLong));
        if (!manager.getScheduler().move(from, to)) {
            event.replyEmbeds(MusicEmbeds.error(
                            "↕️ Перемещение не выполнено",
                            "Обе позиции должны быть в диапазоне `1.."
                                    + manager.getScheduler().queueSize() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "↕️ Очередь изменена",
                        "Трек перемещён с позиции `" + from + "` на позицию `" + to + "`."))
                .queue();
    }

    private void clear(SlashCommandInteractionEvent event) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        int removed = manager.getScheduler().clearQueue();
        event.replyEmbeds(MusicEmbeds.success(
                        "🧹 Очередь очищена",
                        removed == 0
                                ? "Ожидающих треков уже не было. Текущая песня продолжает играть."
                                : "Удалено ожидающих треков: `" + removed
                                + "`. Текущая песня продолжает играть."))
                .queue();
    }

    private void settings(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "show".equals(subcommand)) {
            GuildPreferences preferences = preferencesRepository.get(event.getGuild().getIdLong());
            event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
            return;
        }

        if (!allowManageSettings(event)) {
            return;
        }

        switch (subcommand) {
            case "volume" -> updateDefaultVolume(event);
            case "repeat" -> updateDefaultRepeat(event);
            case "reset" -> resetSettings(event);
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "⚙️ Неизвестная настройка",
                            "Используй `/settings show`, `/settings volume`, `/settings repeat` или `/settings reset`."))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void updateDefaultVolume(SlashCommandInteractionEvent event) {
        long requested = event.getOption("level", -1L, OptionMapping::getAsLong);
        if (requested < 0 || requested > musicProperties.getMaxVolume()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔊 Недопустимая громкость",
                            "Укажи значение от `0` до `" + musicProperties.getMaxVolume() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int volume = Math.toIntExact(requested);
        GuildPreferences preferences = preferencesRepository.saveVolume(event.getGuild().getIdLong(), volume);
        playerManager.findMusicManager(event.getGuild()).ifPresent(manager -> {
            manager.getAudioPlayer().setVolume(volume);
            manager.markActivity();
        });
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateDefaultRepeat(SlashCommandInteractionEvent event) {
        String rawMode = event.getOption("mode", "off", OptionMapping::getAsString);
        RepeatMode mode;
        try {
            mode = RepeatMode.parse(rawMode);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔁 Неизвестный режим",
                            "Доступны режимы: `off`, `track` и `queue`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveRepeatMode(
                event.getGuild().getIdLong(), mode);
        playerManager.findMusicManager(event.getGuild())
                .ifPresent(manager -> manager.getScheduler().setRepeatMode(mode));
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void resetSettings(SlashCommandInteractionEvent event) {
        GuildPreferences preferences = preferencesRepository.reset(event.getGuild().getIdLong());
        playerManager.findMusicManager(event.getGuild()).ifPresent(manager -> {
            manager.getAudioPlayer().setVolume(preferences.volume());
            manager.getScheduler().setRepeatMode(preferences.repeatMode());
        });
        event.replyEmbeds(settingsEmbed(preferences))
                .setEphemeral(true)
                .queue();
    }

    private boolean allowManageSettings(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member.isOwner() || member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        event.replyEmbeds(MusicEmbeds.error(
                        "🔐 Недостаточно прав",
                        "Изменять постоянные настройки может владелец сервера или участник с правом `Manage Server`."))
                .setEphemeral(true)
                .queue();
        return false;
    }

    private MessageEmbed settingsEmbed(GuildPreferences preferences) {
        return new EmbedBuilder()
                .setTitle("⚙️ Настройки музыкального сервера")
                .setColor(Color.ORANGE)
                .addField("Громкость новых сессий", "`" + preferences.volume() + "%`", true)
                .addField("Повтор новых сессий", "`" + preferences.repeatMode().label() + "`", true)
                .setFooter("Настройки сохраняются между перезапусками и применяются к активной сессии сразу")
                .build();
    }

    private GuildMusicManager activeManager(SlashCommandInteractionEvent event) {
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎵 Музыкальной сессии нет",
                            "Сначала добавь песню через `/play`."))
                    .setEphemeral(true)
                    .queue();
        }
        return manager;
    }

    private boolean allowControl(SlashCommandInteractionEvent event) {
        MusicControlPolicy.Decision decision = controlDecision(event.getGuild(), event.getMember());
        if (decision.allowed()) {
            return true;
        }
        event.replyEmbeds(MusicEmbeds.error("🎧 Управление недоступно", decision.message()))
                .setEphemeral(true)
                .queue();
        return false;
    }

    private MusicControlPolicy.Decision controlDecision(Guild guild, Member member) {
        return controlPolicy.canControlPlayback(
                member,
                member.getVoiceState(),
                guild.getSelfMember().getVoiceState());
    }

    private AudioPlayer currentPlayer(Guild guild) {
        return playerManager.findMusicManager(guild)
                .map(GuildMusicManager::getAudioPlayer)
                .orElse(null);
    }

    private void toggleFromButton(ButtonInteractionEvent event, Guild guild) {
        AudioPlayer player = currentPlayer(guild);
        if (player == null || player.getPlayingTrack() == null) {
            event.replyEmbeds(MusicEmbeds.error("🎵 Сейчас тишина", "Сейчас нечего переключать."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        boolean paused = !player.isPaused();
        player.setPaused(paused);
        event.replyEmbeds(MusicEmbeds.success(
                        paused ? "⏸️ Пауза" : "▶️ Продолжено",
                        "Текущая песня: `" + player.getPlayingTrack().getInfo().title + "`"))
                .setEphemeral(true)
                .queue();
    }

    private void previousFromButton(ButtonInteractionEvent event, Guild guild) {
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎵 Музыкальной сессии нет",
                            "Сначала добавь песню через `/play`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        TrackScheduler.PreviousResult result = manager.getScheduler().previousTrack();
        if (result.status() != TrackScheduler.PreviousStatus.STARTED) {
            String details = result.status() == TrackScheduler.PreviousStatus.NO_HISTORY
                    ? "История текущей сессии пока пуста."
                    : "Очередь и история достигли безопасного лимита.";
            event.replyEmbeds(MusicEmbeds.error("⏮️ Предыдущий трек недоступен", details))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "⏮️ Предыдущий трек",
                        "Сейчас играет: `" + result.request().track().getInfo().title + "`."))
                .setEphemeral(true)
                .queue();
    }

    private void seekFromButton(ButtonInteractionEvent event, Guild guild, long deltaMillis) {
        AudioPlayer player = currentPlayer(guild);
        AudioTrack track = player == null ? null : player.getPlayingTrack();
        if (track == null) {
            event.replyEmbeds(MusicEmbeds.error("🎵 Сейчас тишина", "Перематывать нечего."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!track.isSeekable()) {
            event.replyEmbeds(MusicEmbeds.error("⏳ Перемотка недоступна", "Этот источник нельзя перематывать."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        long target = Math.max(0L, Math.min(track.getPosition() + deltaMillis, track.getDuration()));
        track.setPosition(target);
        event.replyEmbeds(MusicEmbeds.success(
                        deltaMillis < 0 ? "⏪ Назад на 15 секунд" : "⏩ Вперёд на 15 секунд",
                        "Новая позиция: `" + MusicEmbeds.formatTime(target) + "`."))
                .setEphemeral(true)
                .queue();
    }

    private void shuffleFromButton(ButtonInteractionEvent event, Guild guild) {
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        int size = manager == null ? 0 : manager.getScheduler().shuffleQueue();
        if (size < 2) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔀 Перемешивать нечего",
                            "В очереди должно быть хотя бы два ожидающих трека."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "🔀 Очередь перемешана",
                        "Новый порядок применён к `" + size + "` трекам."))
                .setEphemeral(true)
                .queue();
    }

    private void skipFromButton(ButtonInteractionEvent event, Guild guild) {
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        AudioTrack current = manager == null ? null : manager.getAudioPlayer().getPlayingTrack();
        if (current == null) {
            event.replyEmbeds(MusicEmbeds.error("⏭️ Нечего пропускать", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        TrackRequest next = manager.getScheduler().nextTrack();
        event.replyEmbeds(MusicEmbeds.success(
                        "⏭️ Песня пропущена",
                        next == null ? "Очередь пуста." : "Сейчас играет: `" + next.track().getInfo().title + "`"))
                .setEphemeral(true)
                .queue();
    }

    private void stopFromButton(ButtonInteractionEvent event, Guild guild) {
        AudioPlayer player = currentPlayer(guild);
        if (player == null || player.getPlayingTrack() == null) {
            event.replyEmbeds(MusicEmbeds.error("⏹️ Уже остановлено", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        playerManager.stopAndRelease(guild);
        event.replyEmbeds(MusicEmbeds.success(
                        "⏹️ Воспроизведение остановлено",
                        "Очередь очищена, бот отключён от голосового канала."))
                .setEphemeral(true)
                .queue();
    }

    private void repeatFromButton(ButtonInteractionEvent event, Guild guild) {
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎵 Музыкальной сессии нет",
                            "Сначала добавь песню через `/play`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        RepeatMode mode = manager.getScheduler().cycleRepeatMode();
        event.replyEmbeds(MusicEmbeds.success(
                        "🔁 Режим повтора изменён",
                        "Текущий режим: `" + mode.label() + "`."))
                .setEphemeral(true)
                .queue();
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0) {
            return "%dд %02d:%02d:%02d".formatted(days, hours, minutes, remainingSeconds);
        }
        return "%02d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
    }

    private MessageEmbed helpEmbed() {
        return new EmbedBuilder()
                .setTitle("🎤 Современные команды Баскова")
                .setDescription("Slash-команды — основной интерфейс. Старые `!`-команды пока продолжают работать.")
                .setColor(Color.CYAN)
                .addField("▶️ Воспроизведение", "`/play` `/pause` `/resume` `/previous` `/skip` `/stop` `/seek`", false)
                .addField("📋 Очередь", "`/queue` `/remove` `/move` `/shuffle` `/clear`", false)
                .addField("🎚️ Режимы", "`/volume` `/repeat` `/now`", false)
                .addField("⚙️ Настройки", "`/settings show` `/settings volume` `/settings repeat` `/settings reset`", false)
                .addField("ℹ️ Сервис", "`/version` `/status` `/help`", false)
                .addField("🖱️ Кнопки", "Под `/now` доступны предыдущий трек, ±15 секунд, пауза, следующий трек, shuffle, repeat, очередь и stop.", false)
                .addField("💡 Autocomplete", "`/play` предлагает твои недавние поисковые запросы.", false)
                .build();
    }
}
