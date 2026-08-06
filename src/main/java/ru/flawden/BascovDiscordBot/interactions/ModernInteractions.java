package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
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
import ru.flawden.BascovDiscordBot.lavaplayer.BatchMusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicLoadResult;
import ru.flawden.BascovDiscordBot.lavaplayer.MusicSearchResult;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlaybackReadinessResult;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackScheduler;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;
import ru.flawden.BascovDiscordBot.lavaplayer.VoiceConnectionResult;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.PlaylistOperationResult;
import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.operations.JdaRuntimeInfo;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;
import ru.flawden.BascovDiscordBot.settings.PlaybackAccessMode;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
    private final SearchSelectionStore searchSelections;
    private final VoteSkipService voteSkipService;
    private final MusicLibraryRepository musicLibraryRepository;
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
            SearchSelectionStore searchSelections,
            VoteSkipService voteSkipService,
            MusicLibraryRepository musicLibraryRepository,
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
        this.searchSelections = searchSelections;
        this.voteSkipService = voteSkipService;
        this.musicLibraryRepository = musicLibraryRepository;
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
                case "search" -> search(event);
                case "history" -> history(event);
                case "replay" -> replay(event);
                case "playlist" -> playlist(event);
                case "pause" -> pause(event, true);
                case "resume" -> pause(event, false);
                case "previous" -> previous(event);
                case "skip" -> skip(event);
                case "voteskip" -> skip(event);
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
        String focusedName = event.getFocusedOption().getName();
        String focusedValue = event.getFocusedOption().getValue();

        if (("play".equals(event.getName()) || "search".equals(event.getName()))
                && "query".equals(focusedName)) {
            List<Command.Choice> choices = searchHistory
                    .suggest(event.getUser().getIdLong(), focusedValue)
                    .stream()
                    .map(query -> new Command.Choice(query, query))
                    .toList();
            replyAutocomplete(event, choices);
            return;
        }

        if ("playlist".equals(event.getName())
                && "name".equals(focusedName)
                && event.getGuild() != null) {
            String normalized = focusedValue.trim().toLowerCase(Locale.ROOT);
            List<Command.Choice> choices = musicLibraryRepository
                    .playlists(event.getGuild().getIdLong())
                    .stream()
                    .map(StoredPlaylist::name)
                    .filter(name -> normalized.isBlank()
                            || name.toLowerCase(Locale.ROOT).contains(normalized))
                    .limit(25)
                    .map(name -> new Command.Choice(name, name))
                    .toList();
            replyAutocomplete(event, choices);
        }
    }

    private void replyAutocomplete(
            CommandAutoCompleteInteractionEvent event,
            List<Command.Choice> choices) {
        event.replyChoices(choices).queue(
                ignored -> { },
                exception -> {
                    if (isExpiredAutocomplete(exception)) {
                        log.debug("Autocomplete interaction expired before reply: user={}, value={}",
                                event.getUser().getId(),
                                event.getFocusedOption().getValue());
                        return;
                    }
                    log.warn("Autocomplete reply failed: user={}, value={}",
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

        var searchAction = MusicControls.searchAction(event.getComponentId());
        if (searchAction.isPresent()) {
            handleSearchButton(event, guild, member, searchAction.get());
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

        if (MusicControls.SKIP.equals(event.getComponentId())) {
            skipFromButton(event, guild, member);
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
            case MusicControls.SHUFFLE -> shuffleFromButton(event, guild);
            case MusicControls.STOP -> stopFromButton(event, guild);
            case MusicControls.REPEAT -> repeatFromButton(event, guild);
            default -> { }
        }
    }

    private void handleSearchButton(
            ButtonInteractionEvent event,
            Guild guild,
            Member member,
            MusicControls.SearchAction action) {
        if (action.type() == MusicControls.SearchActionType.CANCEL) {
            SearchSelectionStore.ClaimStatus status = searchSelections.cancel(
                    action.token(),
                    guild.getIdLong(),
                    event.getUser().getIdLong());
            if (status == SearchSelectionStore.ClaimStatus.FORBIDDEN) {
                event.replyEmbeds(MusicEmbeds.error(
                                "🔐 Это не твой поиск",
                                "Выбрать или отменить результат может только пользователь, вызвавший `/search`."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            String description = status == SearchSelectionStore.ClaimStatus.CANCELLED
                    ? "Результаты удалены. Запусти `/search` снова, когда понадобится."
                    : "Эта поисковая сессия уже завершилась или истекла.";
            event.editMessageEmbeds(MusicEmbeds.success("🛑 Поиск закрыт", description))
                    .setComponents(List.of())
                    .queue();
            return;
        }

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
                            "Войди в голосовой канал и повтори выбор."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        SearchSelectionStore.ClaimResult claim = searchSelections.claim(
                action.token(),
                action.oneBasedIndex(),
                guild.getIdLong(),
                event.getUser().getIdLong());
        if (!claim.claimed()) {
            if (claim.status() == SearchSelectionStore.ClaimStatus.FORBIDDEN) {
                event.replyEmbeds(MusicEmbeds.error(
                                "🔐 Это не твой поиск",
                                "Выбрать результат может только пользователь, вызвавший `/search`."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            event.editMessageEmbeds(MusicEmbeds.error(
                            "⌛ Результаты устарели",
                            "Выбор уже использован или прошло больше пяти минут. Запусти `/search` снова."))
                    .setComponents(List.of())
                    .queue();
            return;
        }

        TrackRequester requester = new TrackRequester(
                event.getUser().getIdLong(),
                member.getEffectiveName());
        event.deferEdit().queue(hook -> playerManager
                .ensureVoiceConnection(guild, targetChannel)
                .whenComplete((connection, failure) -> {
                    if (failure != null) {
                        log.error("Voice connection future failed after search selection in guild {}",
                                guild.getId(), failure);
                        editVoiceFailure(hook, new VoiceConnectionResult(
                                VoiceConnectionResult.Status.FAILED,
                                "Внутренняя ошибка голосового подключения."));
                        return;
                    }
                    if (!connection.connected()) {
                        editVoiceFailure(hook, connection);
                        return;
                    }
                    playerManager.queueLoadedTrack(
                            guild,
                            claim.track(),
                            requester,
                            result -> editLoadResult(hook, guild, result));
                }));
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
        GuildPreferences accessPreferences = preferencesRepository.get(event.getGuild().getIdLong());
        String accessState = String.join("\n",
                "Режим: `" + accessPreferences.accessMode().label() + "`",
                "DJ-роль: " + djRoleLabel(accessPreferences),
                "Vote-skip: `" + accessPreferences.voteSkipPercent() + "%`");
        String libraryState = "Плейлистов: `"
                + musicLibraryRepository.playlists(event.getGuild().getIdLong()).size()
                + "`\nИстория: `"
                + musicLibraryRepository.history(event.getGuild().getIdLong()).size()
                + "/" + MusicLibraryRepository.MAX_HISTORY_PER_GUILD + "`";

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
                        .addField("Persistent library", libraryState, true)
                        .addField("DJ & voting", accessState, true)
                        .addField("Команды с запуска", commandState, false)
                        .setFooter("Health heartbeat обновляется каждые 10 секунд")
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private void search(SlashCommandInteractionEvent event) {
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

        if (!identifier.startsWith(MediaQueryResolver.YOUTUBE_SEARCH_PREFIX)) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔎 Для ссылок используй /play",
                            "`/search` показывает варианты только для текстового поиска YouTube. "
                                    + "Прямую ссылку можно сразу добавить через `/play`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        searchHistory.remember(event.getUser().getIdLong(), rawQuery);
        event.deferReply(true).queue(hook -> playerManager.search(
                event.getGuild(),
                identifier,
                SearchSelectionStore.MAX_CANDIDATES,
                result -> editSearchResult(hook, event, rawQuery, result)));
    }

    private void editSearchResult(
            InteractionHook hook,
            SlashCommandInteractionEvent event,
            String rawQuery,
            MusicSearchResult result) {
        if (result.status() == MusicSearchResult.Status.NO_MATCHES) {
            hook.editOriginalEmbeds(MusicEmbeds.error(
                            "🔎 Ничего не найдено",
                            "YouTube не вернул подходящих треков. Измени запрос и попробуй снова."))
                    .setComponents(List.of())
                    .queue();
            return;
        }
        if (result.status() == MusicSearchResult.Status.LOAD_FAILED) {
            hook.editOriginalEmbeds(MusicEmbeds.error(
                            "❌ Поиск временно недоступен",
                            "YouTube source не смог выполнить поиск. Проверь `/status` и повтори чуть позже."))
                    .setComponents(List.of())
                    .queue();
            return;
        }

        SearchSelectionStore.SearchSession session = searchSelections.create(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                rawQuery,
                result.tracks());
        hook.editOriginalEmbeds(MusicEmbeds.searchResults(
                        rawQuery,
                        session.candidates(),
                        session.expiresAt()))
                .setComponents(MusicControls.searchRows(
                        session.token(),
                        session.candidates().size()))
                .queue();
    }

    private void history(SlashCommandInteractionEvent event) {
        long requestedPage = event.getOption("page", 1L, OptionMapping::getAsLong);
        if (requestedPage < 1L || requestedPage > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📄 Неверная страница",
                            "Номер страницы должен быть положительным целым числом."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.playbackHistory(
                        musicLibraryRepository.history(event.getGuild().getIdLong()),
                        Math.toIntExact(requestedPage)))
                .setEphemeral(true)
                .queue();
    }

    private void replay(SlashCommandInteractionEvent event) {
        long requestedPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        List<StoredTrack> history = musicLibraryRepository.history(event.getGuild().getIdLong());
        if (requestedPosition < 1L || requestedPosition > history.size()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔁 Позиция истории не найдена",
                            history.isEmpty()
                                    ? "История пока пуста."
                                    : "Укажи номер из диапазона `1.." + history.size() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        StoredTrack selected = history.get(Math.toIntExact(requestedPosition - 1L));
        queueStoredTracks(
                event,
                List.of(selected),
                "🔁 Трек из истории добавлен");
    }

    private void playlist(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "list".equals(subcommand)) {
            event.replyEmbeds(MusicEmbeds.playlistList(
                            musicLibraryRepository.playlists(event.getGuild().getIdLong())))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String name = event.getOption("name", "", OptionMapping::getAsString);
        try {
            switch (subcommand) {
                case "create" -> createPlaylist(event, name);
                case "show" -> showPlaylist(event, name);
                case "add" -> addCurrentTrackToPlaylist(event, name);
                case "play" -> playPlaylist(event, name);
                case "remove" -> removeTrackFromPlaylist(event, name);
                case "delete" -> deletePlaylist(event, name);
                default -> event.replyEmbeds(MusicEmbeds.error(
                                "📚 Неизвестная операция",
                                "Используй `/playlist list`, `create`, `show`, `add`, `play`, `remove` или `delete`."))
                        .setEphemeral(true)
                        .queue();
            }
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📚 Название отклонено",
                            exception.getMessage()))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void createPlaylist(SlashCommandInteractionEvent event, String name) {
        PlaylistOperationResult result = musicLibraryRepository.createPlaylist(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                name);
        replyPlaylistMutation(event, result);
    }

    private void showPlaylist(SlashCommandInteractionEvent event, String name) {
        long requestedPage = event.getOption("page", 1L, OptionMapping::getAsLong);
        if (requestedPage < 1L || requestedPage > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📄 Неверная страница",
                            "Номер страницы должен быть положительным целым числом."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        StoredPlaylist playlist = musicLibraryRepository
                .playlist(event.getGuild().getIdLong(), name)
                .orElse(null);
        event.replyEmbeds(MusicEmbeds.playlistView(
                        playlist,
                        Math.toIntExact(requestedPage)))
                .setEphemeral(true)
                .queue();
    }

    private void addCurrentTrackToPlaylist(SlashCommandInteractionEvent event, String name) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        TrackRequest current = manager == null ? null : manager.getScheduler().getCurrentRequest();
        StoredTrack track = StoredTrack.from(current).orElse(null);
        PlaylistOperationResult result = musicLibraryRepository.addTrack(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                isLibraryAdministrator(event.getMember()),
                track);
        replyPlaylistMutation(event, result);
    }

    private void playPlaylist(SlashCommandInteractionEvent event, String name) {
        StoredPlaylist playlist = musicLibraryRepository
                .playlist(event.getGuild().getIdLong(), name)
                .orElse(null);
        if (playlist == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📚 Плейлист не найден",
                            "Проверь название через `/playlist list`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (playlist.tracks().isEmpty()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📭 Плейлист пуст",
                            "Запусти музыку и добавь текущий трек через `/playlist add`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        queueStoredTracks(
                event,
                playlist.tracks(),
                "📚 Плейлист `" + playlist.name() + "` добавлен");
    }

    private void removeTrackFromPlaylist(SlashCommandInteractionEvent event, String name) {
        long requestedPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        if (requestedPosition < 1L || requestedPosition > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🗑️ Неверная позиция",
                            "Позиция должна быть положительным целым числом."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        PlaylistOperationResult result = musicLibraryRepository.removeTrack(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                isLibraryAdministrator(event.getMember()),
                Math.toIntExact(requestedPosition));
        replyPlaylistMutation(event, result);
    }

    private void deletePlaylist(SlashCommandInteractionEvent event, String name) {
        PlaylistOperationResult result = musicLibraryRepository.deletePlaylist(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                isLibraryAdministrator(event.getMember()));
        replyPlaylistMutation(event, result);
    }

    private void replyPlaylistMutation(
            SlashCommandInteractionEvent event,
            PlaylistOperationResult result) {
        MessageEmbed embed = switch (result.status()) {
            case CREATED -> MusicEmbeds.success(
                    "📚 Плейлист создан",
                    "Создан плейлист `" + result.playlist().name() + "`. Добавляй текущую песню через `/playlist add`.");
            case ADDED -> MusicEmbeds.success(
                    "➕ Трек сохранён",
                    "`" + result.track().title() + "` добавлен в `" + result.playlist().name()
                            + "`. Треков: `" + result.playlist().tracks().size() + "`.");
            case REMOVED -> MusicEmbeds.success(
                    "🗑️ Трек удалён из плейлиста",
                    "Удалён `" + result.track().title() + "`. Осталось: `"
                            + result.playlist().tracks().size() + "`.");
            case DELETED -> MusicEmbeds.success(
                    "🗑️ Плейлист удалён",
                    "Плейлист `" + result.playlist().name() + "` удалён.");
            case ALREADY_EXISTS -> MusicEmbeds.error(
                    "📚 Такой плейлист уже есть",
                    "Используй существующий `" + result.playlist().name() + "` или выбери другое название.");
            case NOT_FOUND -> MusicEmbeds.error(
                    "📚 Плейлист не найден",
                    "Проверь название через `/playlist list`.");
            case FORBIDDEN -> MusicEmbeds.error(
                    "🔐 Недостаточно прав",
                    "Изменять этот плейлист может его создатель или участник с правом `Manage Server`.");
            case PLAYLIST_LIMIT_REACHED -> MusicEmbeds.error(
                    "🚧 Лимит плейлистов достигнут",
                    "На сервере можно хранить до `" + MusicLibraryRepository.MAX_PLAYLISTS_PER_GUILD + "` плейлистов.");
            case TRACK_LIMIT_REACHED -> MusicEmbeds.error(
                    "🚧 Плейлист заполнен",
                    "В одном плейлисте можно хранить до `" + MusicLibraryRepository.MAX_TRACKS_PER_PLAYLIST + "` треков.");
            case INVALID_POSITION -> MusicEmbeds.error(
                    "🗑️ Позиция не найдена",
                    "Проверь номер через `/playlist show`.");
            case UNREPLAYABLE_TRACK -> MusicEmbeds.error(
                    "💾 Текущий трек нельзя сохранить",
                    "Сейчас ничего не играет либо источник не содержит повторно загружаемую YouTube/SoundCloud-ссылку.");
        };
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private void queueStoredTracks(
            SlashCommandInteractionEvent event,
            List<StoredTrack> tracks,
            String successTitle) {
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
                            "Войди в голосовой канал и повтори команду."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        List<String> identifiers = tracks.stream()
                .map(StoredTrack::playbackIdentifier)
                .toList();
        TrackRequester requester = new TrackRequester(
                event.getUser().getIdLong(),
                member.getEffectiveName());
        event.deferReply().queue(hook -> playerManager
                .ensureVoiceConnection(guild, targetChannel)
                .whenComplete((connection, failure) -> {
                    if (failure != null) {
                        log.error("Voice connection future failed while loading stored tracks in guild {}",
                                guild.getId(), failure);
                        editVoiceFailure(hook, new VoiceConnectionResult(
                                VoiceConnectionResult.Status.FAILED,
                                "Внутренняя ошибка голосового подключения."));
                        return;
                    }
                    if (!connection.connected()) {
                        editVoiceFailure(hook, connection);
                        return;
                    }
                    playerManager.loadBatch(
                            guild,
                            identifiers,
                            requester,
                            result -> editBatchLoadResult(hook, guild, successTitle, result));
                }));
    }

    private void editBatchLoadResult(
            InteractionHook hook,
            Guild guild,
            String successTitle,
            BatchMusicLoadResult result) {
        if (result.firstStartedTrack() == null) {
            var action = hook.editOriginalEmbeds(MusicEmbeds.batchLoadResult(successTitle, result));
            if (result.accepted() > 0) {
                action.setComponents(MusicControls.rows());
            }
            action.queue();
            return;
        }

        playerManager.awaitPlaybackReady(guild, result.firstStartedTrack())
                .whenComplete((readiness, failure) -> {
                    if (failure != null) {
                        log.error("Stored batch playback readiness failed in guild {}", guild.getId(), failure);
                        hook.editOriginalEmbeds(MusicEmbeds.error(
                                        "❌ Не удалось подтвердить воспроизведение",
                                        "Список загружен, но проверка Discord media transport завершилась ошибкой."))
                                .setComponents(List.of())
                                .queue();
                        return;
                    }
                    if (readiness.ready()) {
                        hook.editOriginalEmbeds(MusicEmbeds.batchLoadResult(successTitle, result))
                                .setComponents(MusicControls.rows())
                                .queue();
                        return;
                    }
                    hook.editOriginalEmbeds(MusicEmbeds.playbackReadinessFailure(
                                    readiness,
                                    JdaRuntimeInfo.version()))
                            .setComponents(List.of())
                            .queue();
                });
    }

    private static boolean isLibraryAdministrator(Member member) {
        return member != null && (member.isOwner() || member.hasPermission(Permission.MANAGE_SERVER));
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
        Guild guild = event.getGuild();
        Member member = event.getMember();
        MusicControlPolicy.SkipDecision decision = skipDecision(guild, member);
        if (decision.access() == MusicControlPolicy.SkipAccess.DENIED) {
            event.replyEmbeds(MusicEmbeds.error("🎧 Пропуск недоступен", decision.message()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        AudioTrack current = manager == null ? null : manager.getAudioPlayer().getPlayingTrack();
        if (current == null) {
            event.replyEmbeds(MusicEmbeds.error("⏭️ Нечего пропускать", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (decision.access() == MusicControlPolicy.SkipAccess.VOTE) {
            VoteSkipService.VoteResult result = castSkipVote(guild, member, current);
            if (result.status() != VoteSkipService.VoteStatus.PASSED) {
                replyVoteProgress(event, result);
                return;
            }
        }

        TrackRequest next = manager.getScheduler().nextTrack();
        voteSkipService.reset(guild.getIdLong());
        String description = next == null
                ? "Песня `" + current.getInfo().title + "` пропущена. Очередь пуста."
                : "Песня `" + current.getInfo().title + "` пропущена.\nСейчас играет: `"
                + next.track().getInfo().title + "`";
        String title = decision.access() == MusicControlPolicy.SkipAccess.VOTE
                ? "🗳️ Голосование прошло"
                : "⏭️ Песня пропущена";
        event.replyEmbeds(MusicEmbeds.success(title, description))
                .setComponents(MusicControls.rows())
                .queue();
    }

    private VoteSkipService.VoteResult castSkipVote(Guild guild, Member member, AudioTrack current) {
        GuildPreferences preferences = preferencesRepository.get(guild.getIdLong());
        int eligibleListeners = eligibleHumanListeners(guild);
        return voteSkipService.vote(
                guild.getIdLong(),
                playbackVoteKey(current),
                member.getIdLong(),
                eligibleListeners,
                preferences.voteSkipPercent());
    }

    private void replyVoteProgress(
            SlashCommandInteractionEvent event,
            VoteSkipService.VoteResult result) {
        String title = result.status() == VoteSkipService.VoteStatus.DUPLICATE
                ? "🗳️ Голос уже учтён"
                : "🗳️ Голос принят";
        String description = "Голосов: `" + result.votes() + "/" + result.requiredVotes() + "`. "
                + "Слушателей в канале: `" + result.eligibleListeners() + "`.";
        event.replyEmbeds(MusicEmbeds.success(title, description))
                .setEphemeral(true)
                .queue();
    }

    private int eligibleHumanListeners(Guild guild) {
        var botChannel = guild.getSelfMember().getVoiceState().getChannel();
        if (botChannel == null) {
            return 1;
        }
        long channelId = botChannel.getIdLong();
        long count = guild.getVoiceStates().stream()
                .filter(state -> state.inAudioChannel() && state.getChannel() != null)
                .filter(state -> state.getChannel().getIdLong() == channelId)
                .map(state -> state.getMember())
                .filter(candidate -> !candidate.getUser().isBot())
                .count();
        return Math.max(1, Math.toIntExact(count));
    }

    private static String playbackVoteKey(AudioTrack track) {
        String identifier = track.getIdentifier() == null ? "unknown" : track.getIdentifier();
        return identifier + "#" + Integer.toUnsignedString(System.identityHashCode(track));
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
        voteSkipService.reset(guild.getIdLong());
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
            case "access" -> updateAccessMode(event);
            case "dj-role" -> updateDjRole(event);
            case "vote-threshold" -> updateVoteThreshold(event);
            case "reset" -> resetSettings(event);
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "⚙️ Неизвестная настройка",
                            "Используй `/settings show`, `/settings access`, `/settings dj-role`, "
                                    + "`/settings vote-threshold`, `/settings volume`, `/settings repeat` или `/settings reset`."))
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

    private void updateAccessMode(SlashCommandInteractionEvent event) {
        String rawMode = event.getOption("mode", "open", OptionMapping::getAsString);
        PlaybackAccessMode mode;
        try {
            mode = PlaybackAccessMode.parse(rawMode);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎛️ Неизвестный режим доступа",
                            "Доступны режимы: `open`, `dj` и `vote`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveAccessMode(
                event.getGuild().getIdLong(), mode);
        voteSkipService.reset(event.getGuild().getIdLong());
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateDjRole(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", null, OptionMapping::getAsRole);
        if (role != null && role.isPublicRole()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎧 Нельзя использовать @everyone",
                            "Выбери отдельную роль для DJ или оставь поле пустым, чтобы очистить настройку."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveDjRoleId(
                event.getGuild().getIdLong(), role == null ? 0L : role.getIdLong());
        voteSkipService.reset(event.getGuild().getIdLong());
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateVoteThreshold(SlashCommandInteractionEvent event) {
        long requested = event.getOption("percent", -1L, OptionMapping::getAsLong);
        if (requested < 25 || requested > 100) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🗳️ Недопустимый порог",
                            "Укажи значение от `25` до `100` процентов слушателей."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveVoteSkipPercent(
                event.getGuild().getIdLong(), Math.toIntExact(requested));
        voteSkipService.reset(event.getGuild().getIdLong());
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void resetSettings(SlashCommandInteractionEvent event) {
        GuildPreferences preferences = preferencesRepository.reset(event.getGuild().getIdLong());
        voteSkipService.reset(event.getGuild().getIdLong());
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
                .addField("Доступ к управлению", "`" + preferences.accessMode().label() + "`", true)
                .addField("DJ-роль", djRoleLabel(preferences), true)
                .addField("Порог vote-skip", "`" + preferences.voteSkipPercent() + "%`", true)
                .setFooter("Настройки сохраняются между перезапусками; очередь доступна всем слушателям")
                .build();
    }

    private static String djRoleLabel(GuildPreferences preferences) {
        return preferences.hasDjRole() ? "<@&" + preferences.djRoleId() + ">" : "`не назначена`";
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

    private MusicControlPolicy.SkipDecision skipDecision(Guild guild, Member member) {
        return controlPolicy.canSkip(
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

    private void skipFromButton(ButtonInteractionEvent event, Guild guild, Member member) {
        MusicControlPolicy.SkipDecision decision = skipDecision(guild, member);
        if (decision.access() == MusicControlPolicy.SkipAccess.DENIED) {
            event.replyEmbeds(MusicEmbeds.error("🎧 Пропуск недоступен", decision.message()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        AudioTrack current = manager == null ? null : manager.getAudioPlayer().getPlayingTrack();
        if (current == null) {
            event.replyEmbeds(MusicEmbeds.error("⏭️ Нечего пропускать", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (decision.access() == MusicControlPolicy.SkipAccess.VOTE) {
            VoteSkipService.VoteResult result = castSkipVote(guild, member, current);
            if (result.status() != VoteSkipService.VoteStatus.PASSED) {
                String title = result.status() == VoteSkipService.VoteStatus.DUPLICATE
                        ? "🗳️ Голос уже учтён"
                        : "🗳️ Голос принят";
                event.replyEmbeds(MusicEmbeds.success(
                                title,
                                "Голосов: `" + result.votes() + "/" + result.requiredVotes()
                                        + "`. Слушателей: `" + result.eligibleListeners() + "`."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
        }

        TrackRequest next = manager.getScheduler().nextTrack();
        voteSkipService.reset(guild.getIdLong());
        event.replyEmbeds(MusicEmbeds.success(
                        decision.access() == MusicControlPolicy.SkipAccess.VOTE
                                ? "🗳️ Голосование прошло"
                                : "⏭️ Песня пропущена",
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
        voteSkipService.reset(guild.getIdLong());
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
                .addField("▶️ Воспроизведение", "`/play` `/search` `/pause` `/resume` `/previous` `/skip` `/voteskip` `/stop` `/seek`", false)
                .addField("📋 Очередь", "`/queue` `/remove` `/move` `/shuffle` `/clear`", false)
                .addField("📚 Библиотека", "`/playlist` `/history` `/replay`", false)
                .addField("🎚️ Режимы", "`/volume` `/repeat` `/now`", false)
                .addField("⚙️ Настройки", "`/settings show` `/settings access` `/settings dj-role` `/settings vote-threshold` `/settings volume` `/settings repeat` `/settings reset`", false)
                .addField("ℹ️ Сервис", "`/version` `/status` `/help`", false)
                .addField("🖱️ Кнопки", "Под `/now` доступны предыдущий трек, ±15 секунд, пауза, следующий трек, shuffle, repeat, очередь и stop.", false)
                .addField("🔎 Выбор трека", "`/search` показывает до пяти результатов YouTube с кнопками выбора. Результаты живут пять минут и доступны только автору.", false)
                .addField("💡 Autocomplete", "`/play` и `/search` предлагают недавние запросы, а `/playlist` — сохранённые названия.", false)
                .addField("🎧 DJ и голосование", "Владелец или `Manage Server` может назначить DJ-роль и выбрать режим `open`, `dj` или `vote`. В режиме vote кнопка `Следующий` тоже считает голос.", false)
                .addField("💾 Постоянное хранение", "Плейлисты, история и правила DJ переживают restart контейнера.", false)
                .build();
    }
}
