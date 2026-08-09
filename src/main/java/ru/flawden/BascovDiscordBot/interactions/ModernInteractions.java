package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
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
import ru.flawden.BascovDiscordBot.lavaplayer.RadioStartResult;
import ru.flawden.BascovDiscordBot.lavaplayer.RadioSnapshot;
import ru.flawden.BascovDiscordBot.lavaplayer.RadioMode;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackScheduler;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;
import ru.flawden.BascovDiscordBot.lavaplayer.VoiceConnectionResult;
import ru.flawden.BascovDiscordBot.library.FavoriteOperationResult;
import ru.flawden.BascovDiscordBot.library.FavoriteSearchHit;
import ru.flawden.BascovDiscordBot.library.PersonalListeningInsights;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.PlaylistOperationResult;
import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.operations.JdaRuntimeInfo;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.PersistenceBackupService;
import ru.flawden.BascovDiscordBot.operations.PersistenceReadiness;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.SystemDoctor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.session.SessionRecoveryDetails;
import ru.flawden.BascovDiscordBot.session.SessionRecoverySnapshot;
import ru.flawden.BascovDiscordBot.settings.GuildAdministrationPolicy;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildSettingsAuditEntry;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;
import ru.flawden.BascovDiscordBot.settings.PlaybackAccessMode;
import ru.flawden.BascovDiscordBot.settings.QueueModerationPolicy;
import ru.flawden.BascovDiscordBot.settings.RequestAccessMode;
import ru.flawden.BascovDiscordBot.settings.SettingsProfileCodec;

import java.awt.Color;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final GuildAdministrationPolicy administrationPolicy;
    private final QueueModerationPolicy moderationPolicy;
    private final OperationalMetrics operationalMetrics;
    private final RuntimeHealthMonitor healthMonitor;
    private final SystemDoctor systemDoctor;
    private final PersistenceReadiness persistenceReadiness;
    private final PersistenceBackupService persistenceBackupService;
    private final DaveRuntimeInfo daveRuntimeInfo;
    private final ConfirmationStore confirmationStore;

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
            GuildAdministrationPolicy administrationPolicy,
            QueueModerationPolicy moderationPolicy,
            OperationalMetrics operationalMetrics,
            RuntimeHealthMonitor healthMonitor,
            SystemDoctor systemDoctor,
            PersistenceReadiness persistenceReadiness,
            PersistenceBackupService persistenceBackupService,
            DaveRuntimeInfo daveRuntimeInfo,
            ConfirmationStore confirmationStore) {
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
        this.administrationPolicy = administrationPolicy;
        this.moderationPolicy = moderationPolicy;
        this.operationalMetrics = operationalMetrics;
        this.healthMonitor = healthMonitor;
        this.systemDoctor = systemDoctor;
        this.persistenceReadiness = persistenceReadiness;
        this.persistenceBackupService = persistenceBackupService;
        this.daveRuntimeInfo = daveRuntimeInfo;
        this.confirmationStore = confirmationStore;
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
                case "help" -> help(event);
                case "version" -> event.replyEmbeds(versionEvent.buildEmbed()).setEphemeral(true).queue();
                case "status" -> status(event);
                case "doctor" -> doctor(event);
                case "radio" -> radio(event);
                case "session" -> session(event);
                case "play" -> play(event);
                case "search" -> search(event);
                case "discover" -> discover(event);
                case "history" -> history(event);
                case "replay" -> replay(event);
                case "favorites" -> favorites(event);
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
                case "queue-manage" -> queueManage(event);
                case "moderation" -> moderation(event);
                case "settings" -> settings(event);
                default -> event.replyEmbeds(MusicEmbeds.error(
                                "❌ Неизвестная slash-команда",
                                "Обнови список команд Discord или используй `/help`."))
                        .setEphemeral(true)
                        .queue();
            }
            operationalMetrics.recordSuccess(OperationalMetrics.Channel.SLASH);
        } catch (RuntimeException exception) {
            operationalMetrics.recordFailure(OperationalMetrics.Channel.SLASH, "/" + event.getName(), exception);
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
            List<StoredTrack> favorites = List.of();
            List<StoredTrack> personalHistory = List.of();
            List<StoredTrack> history = List.of();
            List<StoredPlaylist> playlists = List.of();
            if (event.getGuild() != null) {
                long guildId = event.getGuild().getIdLong();
                favorites = musicLibraryRepository.favorites(guildId, event.getUser().getIdLong());
                personalHistory = musicLibraryRepository.personalHistory(guildId, event.getUser().getIdLong());
                history = musicLibraryRepository.history(guildId);
                playlists = musicLibraryRepository.playlists(guildId);
            }
            List<Command.Choice> choices = DiscoverySuggestions.suggest(
                            focusedValue,
                            searchHistory.recent(event.getUser().getIdLong(), 20),
                            favorites,
                            personalHistory,
                            history,
                            playlists)
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
        boolean experienceButton = ExperienceControls.supports(event.getComponentId());
        boolean musicButton = MusicControls.supports(event.getComponentId());
        if (!experienceButton && !musicButton) {
            return;
        }
        try {
            if (experienceButton) {
                handleExperienceButton(event);
            } else {
                handleMusicButton(event);
            }
            operationalMetrics.recordSuccess(OperationalMetrics.Channel.BUTTON);
        } catch (RuntimeException exception) {
            operationalMetrics.recordFailure(OperationalMetrics.Channel.BUTTON,
                    experienceButton ? "experience-button" : "music-button", exception);
            log.error("Interaction button '{}' failed for user {}",
                    event.getComponentId(), event.getUser().getId(), exception);
            MessageEmbed failureEmbed = MusicEmbeds.error(
                    "💥 Кнопка не сработала",
                    "Произошла внутренняя ошибка. Попробуй ещё раз чуть позже.");
            if (!event.isAcknowledged()) {
                event.replyEmbeds(failureEmbed)
                        .setEphemeral(true)
                        .queue();
            } else {
                event.getHook().sendMessageEmbeds(failureEmbed)
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    private void handleExperienceButton(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.replyEmbeds(MusicEmbeds.error("🏠 Нужен сервер", "Эта кнопка работает только на сервере."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        var helpSection = ExperienceControls.helpSection(event.getComponentId());
        if (helpSection.isPresent()) {
            ExperienceControls.HelpSection section = helpSection.get();
            event.editMessageEmbeds(helpEmbed(section, member))
                    .setComponents(ExperienceControls.helpRows(section))
                    .queue();
            return;
        }

        if (ExperienceControls.STATUS_REFRESH.equals(event.getComponentId())) {
            event.editMessageEmbeds(statusEmbed(guild))
                    .setComponents(ExperienceControls.statusRows())
                    .queue();
            return;
        }

        var confirmationAction = ExperienceControls.confirmationAction(event.getComponentId());
        if (confirmationAction.isEmpty()) {
            return;
        }

        ExperienceControls.ConfirmationAction componentAction = confirmationAction.get();
        if (componentAction.decision() == ExperienceControls.Decision.CANCEL) {
            ConfirmationStore.ClaimStatus status = confirmationStore.cancel(
                    componentAction.token(),
                    guild.getIdLong(),
                    event.getUser().getIdLong());
            if (status == ConfirmationStore.ClaimStatus.FORBIDDEN) {
                event.replyEmbeds(MusicEmbeds.error(
                                "🔐 Это не твоё подтверждение",
                                "Подтвердить или отменить действие может только пользователь, который его запросил."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            String details = status == ConfirmationStore.ClaimStatus.CANCELLED
                    ? "Никакие данные и playback-состояние не изменены."
                    : "Подтверждение уже использовано или истекло.";
            event.editMessageEmbeds(MusicEmbeds.success("↩️ Действие отменено", details))
                    .setComponents(List.of())
                    .queue();
            return;
        }

        ConfirmationStore.ClaimResult claim = confirmationStore.claim(
                componentAction.token(),
                guild.getIdLong(),
                event.getUser().getIdLong());
        if (!claim.claimed()) {
            if (claim.status() == ConfirmationStore.ClaimStatus.FORBIDDEN) {
                event.replyEmbeds(MusicEmbeds.error(
                                "🔐 Это не твоё подтверждение",
                                "Подтвердить действие может только пользователь, который его запросил."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            event.editMessageEmbeds(MusicEmbeds.error(
                            "⌛ Подтверждение устарело",
                            "Запусти команду ещё раз: подтверждения живут не больше двух минут и одноразовые."))
                    .setComponents(List.of())
                    .queue();
            return;
        }

        executeConfirmedAction(event, guild, member, claim.confirmation());
    }

    private void executeConfirmedAction(
            ButtonInteractionEvent event,
            Guild guild,
            Member member,
            ConfirmationStore.PendingConfirmation confirmation) {
        switch (confirmation.action()) {
            case STOP -> confirmStop(event, guild, member);
            case CLEAR_QUEUE -> confirmClearQueue(event, guild, member);
            case DELETE_PLAYLIST -> confirmDeletePlaylist(event, guild, member, confirmation.payload());
            case CLEAR_FAVORITES -> confirmClearFavorites(event, guild);
            case RESET_SETTINGS -> confirmResetSettings(event, guild, member);
        }
    }

    private void confirmStop(ButtonInteractionEvent event, Guild guild, Member member) {
        MusicControlPolicy.Decision decision = controlDecision(guild, member);
        if (!decision.allowed()) {
            event.editMessageEmbeds(MusicEmbeds.error("🎧 Управление недоступно", decision.message()))
                    .setComponents(List.of())
                    .queue();
            return;
        }
        AudioPlayer player = currentPlayer(guild);
        if (player == null || player.getPlayingTrack() == null) {
            event.editMessageEmbeds(MusicEmbeds.error("⏹️ Уже остановлено", "Сейчас ничего не играет."))
                    .setComponents(List.of())
                    .queue();
            return;
        }
        String title = player.getPlayingTrack().getInfo().title;
        playerManager.stopAndRelease(guild);
        voteSkipService.reset(guild.getIdLong());
        event.editMessageEmbeds(MusicEmbeds.success(
                        "⏹️ Воспроизведение остановлено",
                        "Песня `" + title + "` остановлена, очередь очищена, бот отключён."))
                .setComponents(List.of())
                .queue();
    }

    private void confirmClearQueue(ButtonInteractionEvent event, Guild guild, Member member) {
        MusicControlPolicy.Decision decision = controlDecision(guild, member);
        if (!decision.allowed()) {
            event.editMessageEmbeds(MusicEmbeds.error("🎧 Управление недоступно", decision.message()))
                    .setComponents(List.of())
                    .queue();
            return;
        }
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        if (manager == null) {
            event.editMessageEmbeds(MusicEmbeds.error(
                            "🎵 Музыкальной сессии нет",
                            "Сначала добавь песню через `/play`."))
                    .setComponents(List.of())
                    .queue();
            return;
        }
        int removed = manager.getScheduler().clearQueue();
        event.editMessageEmbeds(MusicEmbeds.success(
                        "🧹 Очередь очищена",
                        removed == 0
                                ? "Ожидающих треков уже не было. Текущая песня продолжает играть."
                                : "Удалено ожидающих треков: `" + removed + "`. Текущая песня продолжает играть."))
                .setComponents(List.of())
                .queue();
    }

    private void confirmDeletePlaylist(
            ButtonInteractionEvent event,
            Guild guild,
            Member member,
            String playlistName) {
        PlaylistOperationResult result = musicLibraryRepository.deletePlaylist(
                guild.getIdLong(),
                playlistName,
                event.getUser().getIdLong(),
                administrationPolicy.canManage(member));
        MessageEmbed embed = switch (result.status()) {
            case DELETED -> MusicEmbeds.success(
                    "🗑️ Плейлист удалён",
                    "Плейлист `" + result.playlist().name() + "` удалён.");
            case NOT_FOUND -> MusicEmbeds.error(
                    "📚 Плейлист уже отсутствует",
                    "Возможно, его удалили после запроса подтверждения.");
            case FORBIDDEN -> MusicEmbeds.error(
                    "🔐 Недостаточно прав",
                    "После запроса подтверждения права изменились; удаление отменено.");
            default -> MusicEmbeds.error(
                    "📚 Удаление не выполнено",
                    "Состояние библиотеки изменилось. Открой `/playlist list` и повтори операцию.");
        };
        event.editMessageEmbeds(embed).setComponents(List.of()).queue();
    }

    private void confirmClearFavorites(ButtonInteractionEvent event, Guild guild) {
        FavoriteOperationResult result = musicLibraryRepository.clearFavorites(
                guild.getIdLong(),
                event.getUser().getIdLong());
        MessageEmbed embed = result.status() == FavoriteOperationResult.Status.CLEARED
                ? MusicEmbeds.success(
                        "🧹 Избранное очищено",
                        "Удалено треков: `" + result.affectedTracks() + "`. Плейлисты и общая история не изменены.")
                : MusicEmbeds.error(
                        "⭐ Избранное уже пусто",
                        "Удалять больше нечего.");
        event.editMessageEmbeds(embed).setComponents(List.of()).queue();
    }

    private void confirmResetSettings(ButtonInteractionEvent event, Guild guild, Member member) {
        if (!administrationPolicy.canManage(member)) {
            event.editMessageEmbeds(MusicEmbeds.error(
                            "🔐 Недостаточно прав",
                            "После запроса подтверждения административные права изменились; сброс отменён."))
                    .setComponents(List.of())
                    .queue();
            return;
        }
        GuildPreferences preferences = preferencesRepository.reset(guild.getIdLong());
        voteSkipService.reset(guild.getIdLong());
        playerManager.findMusicManager(guild).ifPresent(manager -> {
            manager.getAudioPlayer().setVolume(preferences.volume());
            manager.getScheduler().setRepeatMode(preferences.repeatMode());
            manager.getScheduler().setRequesterQueueLimit(preferences.requesterQueueLimit());
        });
        recordSettingsAudit(guild, event.getUser().getIdLong(), "reset-to-defaults");
        event.editMessageEmbeds(settingsEmbed(preferences))
                .setComponents(List.of())
                .queue();
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

        if (MusicControls.QUEUE_MINE.equals(event.getComponentId())) {
            GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
            event.replyEmbeds(MusicEmbeds.personalQueue(manager, event.getUser().getIdLong()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (MusicControls.QUEUE_COMMUNITY.equals(event.getComponentId())) {
            GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
            event.replyEmbeds(MusicEmbeds.queueCommunity(manager))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (MusicControls.VOTE_STATUS.equals(event.getComponentId())) {
            replyVoteStatus(event, guild, member);
            return;
        }

        if (MusicControls.REFRESH.equals(event.getComponentId())) {
            GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
            event.editMessageEmbeds(MusicEmbeds.nowPlaying(manager))
                    .setComponents(MusicControls.nowRows(manager))
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

    private void help(SlashCommandInteractionEvent event) {
        String rawSection = event.getOption(
                "section",
                ExperienceControls.HelpSection.OVERVIEW.id(),
                OptionMapping::getAsString);
        ExperienceControls.HelpSection section = ExperienceControls.HelpSection.parse(rawSection)
                .orElse(ExperienceControls.HelpSection.OVERVIEW);
        event.replyEmbeds(helpEmbed(section, event.getMember()))
                .setComponents(ExperienceControls.helpRows(section))
                .setEphemeral(true)
                .queue();
    }

    private void status(SlashCommandInteractionEvent event) {
        event.replyEmbeds(statusEmbed(event.getGuild()))
                .setComponents(ExperienceControls.statusRows())
                .setEphemeral(true)
                .queue();
    }

    private void doctor(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            subcommand = "summary";
        }
        if ("failures".equals(subcommand)) {
            event.replyEmbeds(doctorFailuresEmbed())
                    .setEphemeral(true)
                    .queue();
            return;
        }

        SystemDoctor.Report report = systemDoctor.diagnose(event.getGuild());
        List<SystemDoctor.Check> checks = switch (subcommand) {
            case "summary" -> report.checks();
            case "gateway" -> report.checks("gateway", "dave");
            case "voice" -> report.checks("voice", "dave");
            case "storage" -> report.checks("storage", "backups");
            case "session" -> report.checks("session");
            case "source" -> report.checks("source");
            default -> List.of();
        };
        if (checks.isEmpty()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "❌ Неизвестный doctor-раздел",
                            "Используй `/doctor summary|gateway|voice|storage|session|source|failures`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(doctorEmbed(subcommand, report, checks))
                .setEphemeral(true)
                .queue();
    }

    private MessageEmbed doctorEmbed(
            String scope,
            SystemDoctor.Report report,
            List<SystemDoctor.Check> checks) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🩺 Baskov Doctor — " + scope)
                .setDescription("Итог: `" + reportSeverity(checks) + "` • проверок: `" + checks.size() + "`")
                .setColor(switch (reportSeverity(checks)) {
                    case OK -> Color.GREEN;
                    case WARN -> Color.ORANGE;
                    case FAIL -> Color.RED;
                });
        for (SystemDoctor.Check check : checks) {
            embed.addField(
                    check.severity().icon() + " " + check.title(),
                    "`" + check.id() + "` • " + sanitizeInline(check.details())
                            + "\n→ " + sanitizeInline(check.action()),
                    false);
        }
        return embed
                .setFooter("/doctor не делает внешних network probes • /doctor failures показывает bounded журнал ошибок")
                .build();
    }

    private MessageEmbed doctorFailuresEmbed() {
        List<OperationalMetrics.FailureEvent> failures = operationalMetrics.recentFailures(10);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🧯 Baskov Doctor — recent failures")
                .setColor(failures.isEmpty() ? Color.GREEN : Color.ORANGE);
        if (failures.isEmpty()) {
            embed.setDescription("С момента запуска bounded журнал внутренних ошибок пуст.");
        } else {
            StringBuilder body = new StringBuilder();
            for (OperationalMetrics.FailureEvent failure : failures) {
                long epoch = failure.at().getEpochSecond();
                body.append("• <t:").append(epoch).append(":R> `")
                        .append(failure.channel()).append("` `")
                        .append(sanitizeInline(failure.operation())).append("` — `")
                        .append(sanitizeInline(failure.errorType())).append("`: ")
                        .append(sanitizeInline(failure.message())).append('\n');
            }
            embed.setDescription(body.toString());
        }
        return embed
                .setFooter("Хранится максимум " + OperationalMetrics.MAX_RECENT_FAILURES
                        + " событий в памяти процесса • user IDs и stack traces сюда не попадают")
                .build();
    }

    private static SystemDoctor.Severity reportSeverity(List<SystemDoctor.Check> checks) {
        return checks.stream()
                .map(SystemDoctor.Check::severity)
                .max(SystemDoctor.Severity::compareTo)
                .orElse(SystemDoctor.Severity.OK);
    }

    private void radio(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        String subcommand = event.getSubcommandName() == null ? "status" : event.getSubcommandName();

        if ("status".equals(subcommand)) {
            event.replyEmbeds(radioEmbed(playerManager.radioSnapshot(guild.getIdLong())))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("stop".equals(subcommand)) {
            RadioSnapshot snapshot = playerManager.radioSnapshot(guild.getIdLong());
            boolean owner = snapshot.enabled() && snapshot.ownerUserId() == event.getUser().getIdLong();
            if (!owner && !administrationPolicy.canManage(member)) {
                event.replyEmbeds(MusicEmbeds.error(
                                "📻 Нельзя выключить чужое радио",
                                "Остановить smart radio может тот, кто его включил, либо owner / Manage Server / manager-role."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!snapshot.enabled()) {
                event.replyEmbeds(MusicEmbeds.error("📻 Радио уже выключено", "Сейчас smart radio не активно."))
                        .setEphemeral(true)
                        .queue();
                return;
            }
            playerManager.stopRadio(guild.getIdLong());
            event.replyEmbeds(MusicEmbeds.success(
                            "📻 Smart radio выключено",
                            "Текущий трек, если он играет, не остановлен. После окончания обычной очереди автопродолжения больше не будет."))
                    .setEphemeral(true)
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

        String rawMode = event.getOption("mode", "personal", OptionMapping::getAsString);
        RadioMode mode = "server".equalsIgnoreCase(rawMode) ? RadioMode.SERVER : RadioMode.PERSONAL;
        long userId = event.getUser().getIdLong();
        if (!playerManager.hasRadioSeeds(guild.getIdLong(), mode, userId)) {
            String hint = mode == RadioMode.PERSONAL
                    ? "Добавь несколько favorites или послушай/закажи треки, чтобы появилась personal history."
                    : "На сервере пока нет replayable history для server-radio.";
            event.replyEmbeds(MusicEmbeds.error("📻 Пока не из чего строить радио", hint))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        var botChannel = guild.getSelfMember().getVoiceState().getChannel();
        var targetChannel = botChannel != null ? botChannel : member.getVoiceState().getChannel();
        if (targetChannel == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔍 Голосовой канал потерян",
                            "Войди в голосовой канал и повтори `/radio start`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackRequester owner = new TrackRequester(userId, member.getEffectiveName());
        event.deferReply().queue(hook -> playerManager
                .ensureVoiceConnection(guild, targetChannel)
                .whenComplete((connection, failure) -> {
                    if (failure != null) {
                        log.error("Voice connection future failed while starting radio in guild {}", guild.getId(), failure);
                        editVoiceFailure(hook, new VoiceConnectionResult(
                                VoiceConnectionResult.Status.FAILED,
                                "Внутренняя ошибка голосового подключения."));
                        return;
                    }
                    if (!connection.connected()) {
                        editVoiceFailure(hook, connection);
                        return;
                    }
                    RadioStartResult result = playerManager.startRadio(guild, mode, owner);
                    String title = result.status() == RadioStartResult.Status.UPDATED
                            ? "📻 Smart radio перенастроено"
                            : "📻 Smart radio включено";
                    hook.editOriginalEmbeds(MusicEmbeds.success(
                                    title,
                                    "Режим: `" + mode.label() + "`. Когда текущая очередь закончится, Басков добавит ровно один безопасный кандидат и продолжит цепочку. "
                                            + "После трёх подряд неудачных refill radio отключится само."))
                            .queue();
                }));
    }

    private MessageEmbed radioEmbed(RadioSnapshot snapshot) {
        if (snapshot == null || !snapshot.enabled()) {
            return MusicEmbeds.success(
                    "📻 Smart radio",
                    "Сейчас выключено. Используй `/radio start` для personal/server autoplay.");
        }
        return new EmbedBuilder()
                .setTitle("📻 Smart radio")
                .setDescription("Состояние: `ON` • режим: `" + snapshot.mode().label() + "`")
                .addField("Включил", snapshot.ownerUserId() > 0L ? "<@" + snapshot.ownerUserId() + ">" : snapshot.ownerDisplayName(), true)
                .addField("Сгенерировано", "`" + snapshot.generatedTracks() + "`", true)
                .addField("Ошибки подряд", "`" + snapshot.consecutiveFailures() + "/3`", true)
                .addField("Refill", snapshot.refillInProgress() ? "`идёт поиск`" : "`ожидание`", true)
                .addField("Последний seed", "`" + sanitizeInline(snapshot.lastSeed()) + "`", false)
                .addField("Последний radio-track", "`" + sanitizeInline(snapshot.lastTrack()) + "`", false)
                .setFooter("Radio-state ephemeral: после restart/deploy режим намеренно остаётся OFF")
                .build();
    }

    private void session(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "status".equals(subcommand)) {
            event.replyEmbeds(sessionRecoveryEmbed(event.getGuild()))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!"recover".equals(subcommand)) {
            event.replyEmbeds(MusicEmbeds.error(
                            "❌ Неизвестная session-команда",
                            "Используй `/session status` или `/session recover`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!administrationPolicy.canManage(event.getMember())) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔒 Недостаточно прав",
                            "Ручной recovery доступен владельцу сервера, участникам с `Manage Server` "
                                    + "или настроенной manager-role."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        PlayerManager.ManualSessionRecoveryResult result = playerManager.retryPersistedSession(event.getGuild());
        boolean started = result.status() == PlayerManager.ManualSessionRecoveryStatus.STARTED;
        event.replyEmbeds(new EmbedBuilder()
                        .setTitle(started ? "♻️ Recovery запущен" : "🧭 Recovery не запущен")
                        .setDescription(result.details())
                        .setColor(started ? Color.GREEN : Color.ORANGE)
                        .addField("Статус", "`" + result.status() + "`", true)
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private MessageEmbed sessionRecoveryEmbed(Guild guild) {
        SessionRecoveryDetails details = playerManager.sessionRecoveryDetails(guild);
        String checkpointAge = details.capturedAtEpochMillis() <= 0L
                ? "—"
                : formatDuration(Duration.between(
                        Instant.ofEpochMilli(details.capturedAtEpochMillis()), Instant.now()).abs());
        String channel = details.voiceChannelId() <= 0L ? "—" : "<#" + details.voiceChannelId() + ">";
        String position = details.resumePositionMillis() <= 0L
                ? "0:00"
                : formatDuration(Duration.ofMillis(details.resumePositionMillis()));
        return new EmbedBuilder()
                .setTitle("♻️ Playback Session Recovery")
                .setColor(details.state() == SessionRecoveryDetails.State.ACTIVE
                        ? Color.GREEN
                        : details.state() == SessionRecoveryDetails.State.NONE ? Color.GRAY : Color.ORANGE)
                .addField("Состояние", "`" + details.state() + "`", true)
                .addField("Voice channel", channel, true)
                .addField("Возраст checkpoint", "`" + checkpointAge + "`", true)
                .addField("Playback", String.join("\n",
                        "Треков current+queue: `" + details.savedTracks() + "`",
                        "Previous history: `" + details.savedHistoryTracks() + "`",
                        "Resume position: `" + position + "`"), true)
                .addField("Modes", String.join("\n",
                        "Paused: `" + details.paused() + "`",
                        "Volume: `" + details.volume() + "%`",
                        "Repeat: `" + details.repeatMode().label() + "`"), true)
                .addField("Последнее recovery-событие", "`" + sanitizeInline(details.lastEvent()) + "`", false)
                .setFooter("/session recover доступен manager/admin и не создаёт новую очередь поверх активной сессии")
                .build();
    }

    private static String sanitizeInline(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String safe = value.replace('`', '\'').replace('\n', ' ').replace('\r', ' ').trim();
        return safe.length() > 900 ? safe.substring(0, 897) + "..." : safe;
    }

    private MessageEmbed statusEmbed(Guild guild) {
        RuntimeHealthMonitor.Snapshot runtime = healthMonitor.snapshot();
        OperationalMetrics.Snapshot commands = operationalMetrics.snapshot();
        MusicRuntimeSnapshot music = playerManager.runtimeSnapshot();
        VoiceDiagnosticSnapshot voice = playerManager.voiceDiagnosticsSnapshot(guild);
        SessionRecoverySnapshot recovery = playerManager.sessionRecoverySnapshot();

        String discord = StatusMessageFormatter.discord(runtime, JdaRuntimeInfo.version());
        String daveState = StatusMessageFormatter.dave(daveRuntimeInfo.snapshot());
        String musicState = StatusMessageFormatter.music(music);
        String playbackState = StatusMessageFormatter.playback(
                playerManager.findMusicManager(guild).orElse(null));
        String voiceState = StatusMessageFormatter.voice(voice);
        String voiceHistory = StatusMessageFormatter.voiceHistory(voice);
        String recoveryState = StatusMessageFormatter.recovery(recovery);
        PersistenceReadiness.Snapshot storage = persistenceReadiness.probe();
        PersistenceBackupService.Snapshot backups = persistenceBackupService.snapshot();
        String storageState = StatusMessageFormatter.storage(storage);
        String backupState = StatusMessageFormatter.backups(backups);
        String reliabilityState = StatusMessageFormatter.reliability(runtime, storage, backups, recovery);
        String commandState = StatusMessageFormatter.commands(commands);
        GuildPreferences accessPreferences = preferencesRepository.get(guild.getIdLong());
        String accessState = String.join("\n",
                "Playback: `" + accessPreferences.accessMode().label() + "`",
                "Requests: `" + accessPreferences.requestAccessMode().label() + "`",
                "DJ-роль: " + djRoleLabel(accessPreferences),
                "Manager-role: " + managerRoleLabel(accessPreferences),
                "Music channel: " + musicChannelLabel(accessPreferences),
                "Vote-skip: `" + accessPreferences.voteSkipPercent() + "%`");
        String libraryState = "Плейлистов: `"
                + musicLibraryRepository.playlists(guild.getIdLong()).size()
                + "`\nИстория: `"
                + musicLibraryRepository.history(guild.getIdLong()).size()
                + "/" + MusicLibraryRepository.MAX_HISTORY_PER_GUILD + "`";

        return new EmbedBuilder()
                .setTitle("🩺 Состояние Baskov Discord Bot")
                .setDescription("Uptime: `" + formatDuration(commands.uptime()) + "`")
                .setColor("CONNECTED".equals(runtime.jdaStatus())
                        && storage.ready()
                        && backups.healthy() ? Color.GREEN : Color.ORANGE)
                .addField("Discord gateway", discord, true)
                .addField("DAVE / E2EE", daveState, true)
                .addField("Музыка", musicState, true)
                .addField("Playback modes", playbackState, true)
                .addField("Voice transport", voiceState, true)
                .addField("Voice history", voiceHistory, false)
                .addField("Voice recovery", recoveryState, false)
                .addField("Persistent library", libraryState, true)
                .addField("Storage readiness", storageState, true)
                .addField("Persistence backups", backupState, true)
                .addField("DJ & voting", accessState, true)
                .addField("Reliability", reliabilityState, true)
                .addField("Команды с запуска", commandState, false)
                .setFooter("Health heartbeat каждые 10 секунд • кнопка ниже пересчитывает live probes")
                .build();
    }

    private void search(SlashCommandInteractionEvent event) {
        String rawQuery = event.getOption("query", "", OptionMapping::getAsString).trim();
        startInteractiveSearch(event, rawQuery);
    }

    private void discover(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "recent".equals(subcommand)) {
            event.replyEmbeds(MusicEmbeds.discoveryRecent(
                            searchHistory.recent(event.getUser().getIdLong(), 10)))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (subcommand) {
            case "again" -> {
                String query = searchHistory.last(event.getUser().getIdLong()).orElse(null);
                if (query == null) {
                    event.replyEmbeds(MusicEmbeds.error(
                                    "🧭 Пока нечего повторять",
                                    "Сначала выполни `/search` или `/play` с текстовым запросом."))
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                startInteractiveSearch(event, query);
            }
            case "related" -> {
                GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
                AudioTrack current = manager == null ? null : manager.getAudioPlayer().getPlayingTrack();
                if (current == null) {
                    event.replyEmbeds(MusicEmbeds.error(
                                    "🧭 Нет текущего трека",
                                    "Запусти музыку, затем повтори `/discover related`."))
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                startInteractiveSearch(event, DiscoverySuggestions.discoveryQuery(
                        current.getInfo().author,
                        current.getInfo().title));
            }
            case "history" -> {
                long position = event.getOption("position", -1L, OptionMapping::getAsLong);
                boolean mine = personalHistoryRequested(event);
                List<StoredTrack> history = historyFor(event, mine);
                if (position < 1L || position > history.size()) {
                    event.replyEmbeds(MusicEmbeds.error(
                                    "🧭 Трек истории не найден",
                                    history.isEmpty()
                                            ? (mine ? "Твоя personal history пока пуста." : "История пока пуста.")
                                            : "Укажи номер из диапазона `1.." + history.size() + "` из `/history`."))
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                StoredTrack track = history.get(Math.toIntExact(position - 1L));
                startInteractiveSearch(event, DiscoverySuggestions.discoveryQuery(
                        track.author(),
                        track.title()));
            }
            case "profile" -> {
                List<StoredTrack> personalHistory = musicLibraryRepository.personalHistory(
                        event.getGuild().getIdLong(),
                        event.getUser().getIdLong());
                List<StoredTrack> favorites = musicLibraryRepository.favorites(
                        event.getGuild().getIdLong(),
                        event.getUser().getIdLong());
                event.replyEmbeds(MusicEmbeds.personalListeningProfile(personalHistory, favorites.size()))
                        .setEphemeral(true)
                        .queue();
            }
            case "for-me" -> {
                long guildId = event.getGuild().getIdLong();
                long userId = event.getUser().getIdLong();
                List<StoredTrack> favorites = musicLibraryRepository.favorites(guildId, userId);
                List<StoredTrack> personalHistory = musicLibraryRepository.personalHistory(guildId, userId);
                StoredTrack seed = PersonalListeningInsights.discoverySeed(favorites, personalHistory).orElse(null);
                if (seed == null) {
                    event.replyEmbeds(MusicEmbeds.error(
                                    "🧭 Пока не хватает личных сигналов",
                                    "Добавь трек в `/favorites` или дождись записей в `/history scope:mine`."))
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                startInteractiveSearch(event, DiscoverySuggestions.discoveryQuery(seed.author(), seed.title()));
            }
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "🧭 Неизвестный режим discovery",
                            "Используй `/discover recent`, `again`, `related`, `history`, `profile` или `for-me`."))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void startInteractiveSearch(
            SlashCommandInteractionEvent event,
            String rawQuery) {
        String safeQuery = rawQuery == null ? "" : rawQuery.trim();
        String identifier;
        try {
            identifier = queryResolver.resolve(safeQuery);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error("🔒 Запрос отклонён", exception.getMessage()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!identifier.startsWith(MediaQueryResolver.YOUTUBE_SEARCH_PREFIX)) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔎 Для ссылок используй /play",
                            "Интерактивный поиск показывает варианты только для текстового поиска YouTube. "
                                    + "Прямую ссылку можно сразу добавить через `/play`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        searchHistory.remember(event.getUser().getIdLong(), safeQuery);
        event.deferReply(true).queue(hook -> playerManager.search(
                event.getGuild(),
                identifier,
                SearchSelectionStore.MAX_CANDIDATES,
                result -> editSearchResult(hook, event, safeQuery, result)));
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
        boolean mine = personalHistoryRequested(event);
        event.replyEmbeds(MusicEmbeds.playbackHistory(
                        historyFor(event, mine),
                        Math.toIntExact(requestedPage),
                        mine))
                .setEphemeral(true)
                .queue();
    }

    private void replay(SlashCommandInteractionEvent event) {
        long requestedPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        boolean mine = personalHistoryRequested(event);
        List<StoredTrack> history = historyFor(event, mine);
        if (requestedPosition < 1L || requestedPosition > history.size()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔁 Позиция истории не найдена",
                            history.isEmpty()
                                    ? (mine ? "Твоя personal history пока пуста." : "История пока пуста.")
                                    : "Укажи номер из диапазона `1.." + history.size() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        StoredTrack selected = history.get(Math.toIntExact(requestedPosition - 1L));
        queueStoredTracks(
                event,
                List.of(selected),
                mine ? "🔁 Твой трек из personal history добавлен" : "🔁 Трек из истории добавлен");
    }

    private List<StoredTrack> historyFor(SlashCommandInteractionEvent event, boolean mine) {
        long guildId = event.getGuild().getIdLong();
        return mine
                ? musicLibraryRepository.personalHistory(guildId, event.getUser().getIdLong())
                : musicLibraryRepository.history(guildId);
    }

    private static boolean personalHistoryRequested(SlashCommandInteractionEvent event) {
        return "mine".equalsIgnoreCase(event.getOption("scope", "server", OptionMapping::getAsString));
    }

    private void favorites(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "list".equals(subcommand)) {
            showFavorites(event);
            return;
        }

        switch (subcommand) {
            case "add" -> addCurrentTrackToFavorites(event);
            case "play" -> playFavorite(event);
            case "play-all" -> playAllFavorites(event);
            case "remove" -> removeFavorite(event);
            case "search" -> searchFavorites(event);
            case "clear" -> clearFavorites(event);
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "⭐ Неизвестная операция",
                            "Используй `/favorites list`, `add`, `play`, `play-all`, `remove`, `search` или `clear`."))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void showFavorites(SlashCommandInteractionEvent event) {
        long requestedPage = event.getOption("page", 1L, OptionMapping::getAsLong);
        if (requestedPage < 1L || requestedPage > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📄 Неверная страница",
                            "Номер страницы должен быть положительным целым числом."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.replyEmbeds(MusicEmbeds.favorites(
                        musicLibraryRepository.favorites(
                                event.getGuild().getIdLong(),
                                event.getUser().getIdLong()),
                        Math.toIntExact(requestedPage)))
                .setEphemeral(true)
                .queue();
    }

    private void addCurrentTrackToFavorites(SlashCommandInteractionEvent event) {
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        TrackRequest current = manager == null ? null : manager.getScheduler().getCurrentRequest();
        StoredTrack track = StoredTrack.from(current).orElse(null);
        FavoriteOperationResult result = musicLibraryRepository.addFavorite(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                track);
        replyFavoriteMutation(event, result);
    }

    private void playFavorite(SlashCommandInteractionEvent event) {
        long requestedPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        List<StoredTrack> favorites = musicLibraryRepository.favorites(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong());
        if (requestedPosition < 1L || requestedPosition > favorites.size()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "⭐ Позиция избранного не найдена",
                            favorites.isEmpty()
                                    ? "Твоё избранное пока пусто."
                                    : "Укажи номер из диапазона `1.." + favorites.size() + "` из `/favorites list`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        queueStoredTracks(
                event,
                List.of(favorites.get(Math.toIntExact(requestedPosition - 1L))),
                "⭐ Трек из избранного добавлен");
    }

    private void playAllFavorites(SlashCommandInteractionEvent event) {
        List<StoredTrack> favorites = musicLibraryRepository.favorites(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong());
        if (favorites.isEmpty()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "⭐ Избранное пусто",
                            "Добавь текущую песню через `/favorites add`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        queueStoredTracks(event, favorites, "⭐ Избранное добавлено в очередь");
    }

    private void removeFavorite(SlashCommandInteractionEvent event) {
        long requestedPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        if (requestedPosition < 1L || requestedPosition > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🗑️ Неверная позиция",
                            "Позиция должна быть положительным целым числом."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        FavoriteOperationResult result = musicLibraryRepository.removeFavorite(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                Math.toIntExact(requestedPosition));
        replyFavoriteMutation(event, result);
    }

    private void searchFavorites(SlashCommandInteractionEvent event) {
        String query = event.getOption("query", "", OptionMapping::getAsString).trim();
        try {
            List<FavoriteSearchHit> hits = musicLibraryRepository.searchFavorites(
                    event.getGuild().getIdLong(),
                    event.getUser().getIdLong(),
                    query);
            event.replyEmbeds(MusicEmbeds.favoriteSearch(query, hits))
                    .setEphemeral(true)
                    .queue();
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error("🔎 Поиск отклонён", exception.getMessage()))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void clearFavorites(SlashCommandInteractionEvent event) {
        List<StoredTrack> favorites = musicLibraryRepository.favorites(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong());
        if (favorites.isEmpty()) {
            event.replyEmbeds(MusicEmbeds.error("⭐ Избранное уже пусто", "Удалять нечего."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        requestConfirmation(
                event,
                ConfirmationStore.Action.CLEAR_FAVORITES,
                "",
                "⚠️ Очистить личное избранное?",
                "Будут удалены все твои `" + favorites.size()
                        + "` сохранённых треков на этом сервере. Плейлисты и общая история останутся без изменений.");
    }

    private void replyFavoriteMutation(
            SlashCommandInteractionEvent event,
            FavoriteOperationResult result) {
        MessageEmbed embed = switch (result.status()) {
            case ADDED -> MusicEmbeds.success(
                    "⭐ Добавлено в избранное",
                    "`" + result.track().title() + "` теперь в твоей личной библиотеке.");
            case ALREADY_EXISTS -> MusicEmbeds.success(
                    "⭐ Уже в избранном",
                    "`" + result.track().title() + "` уже сохранён — дубликат не создан.");
            case REMOVED -> MusicEmbeds.success(
                    "🗑️ Удалено из избранного",
                    "`" + result.track().title() + "` удалён из твоей личной библиотеки.");
            case CLEARED -> MusicEmbeds.success(
                    "🧹 Избранное очищено",
                    "Удалено треков: `" + result.affectedTracks() + "`.");
            case NOT_FOUND -> MusicEmbeds.error(
                    "⭐ Позиция не найдена",
                    "Проверь номер через `/favorites list`.");
            case LIMIT_REACHED -> MusicEmbeds.error(
                    "🚧 Избранное заполнено",
                    "На одном сервере можно хранить до `"
                            + MusicLibraryRepository.MAX_FAVORITES_PER_USER + "` избранных треков на пользователя.");
            case UNREPLAYABLE_TRACK -> MusicEmbeds.error(
                    "💾 Текущий трек нельзя сохранить",
                    "Сейчас ничего не играет либо источник не содержит повторно загружаемую YouTube/SoundCloud-ссылку.");
        };
        event.replyEmbeds(embed).setEphemeral(true).queue();
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
                case "move" -> moveTrackInPlaylist(event, name);
                case "rename" -> renamePlaylist(event, name);
                case "copy" -> copyPlaylist(event, name);
                case "dedupe" -> dedupePlaylist(event, name);
                case "capture-queue" -> captureQueueToPlaylist(event, name);
                case "add-history" -> addHistoryTrackToPlaylist(event, name);
                case "search" -> searchPlaylists(event);
                case "delete" -> deletePlaylist(event, name);
                default -> event.replyEmbeds(MusicEmbeds.error(
                                "📚 Неизвестная операция",
                                "Используй `/playlist list`, `show`, `search` и команды управления библиотекой."))
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
                administrationPolicy.canManage(event.getMember()),
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
                administrationPolicy.canManage(event.getMember()),
                Math.toIntExact(requestedPosition));
        replyPlaylistMutation(event, result);
    }

    private void moveTrackInPlaylist(SlashCommandInteractionEvent event, String name) {
        long from = event.getOption("from", -1L, OptionMapping::getAsLong);
        long to = event.getOption("to", -1L, OptionMapping::getAsLong);
        if (from < 1L || to < 1L || from > Integer.MAX_VALUE || to > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "↕️ Неверная позиция",
                            "Позиции должны быть положительными целыми числами."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        PlaylistOperationResult result = musicLibraryRepository.moveTrack(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                administrationPolicy.canManage(event.getMember()),
                Math.toIntExact(from),
                Math.toIntExact(to));
        replyPlaylistMutation(event, result);
    }

    private void renamePlaylist(SlashCommandInteractionEvent event, String name) {
        String newName = event.getOption("new-name", "", OptionMapping::getAsString);
        PlaylistOperationResult result = musicLibraryRepository.renamePlaylist(
                event.getGuild().getIdLong(),
                name,
                newName,
                event.getUser().getIdLong(),
                administrationPolicy.canManage(event.getMember()));
        replyPlaylistMutation(event, result);
    }

    private void copyPlaylist(SlashCommandInteractionEvent event, String name) {
        String newName = event.getOption("new-name", "", OptionMapping::getAsString);
        PlaylistOperationResult result = musicLibraryRepository.copyPlaylist(
                event.getGuild().getIdLong(),
                name,
                newName,
                event.getUser().getIdLong());
        replyPlaylistMutation(event, result);
    }

    private void dedupePlaylist(SlashCommandInteractionEvent event, String name) {
        PlaylistOperationResult result = musicLibraryRepository.dedupePlaylist(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                administrationPolicy.canManage(event.getMember()));
        replyPlaylistMutation(event, result);
    }

    private void captureQueueToPlaylist(SlashCommandInteractionEvent event, String name) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📭 Очередь пуста",
                            "Сейчас нет музыкальной сессии, которую можно сохранить."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        boolean includeCurrent = event.getOption("include-current", true, OptionMapping::getAsBoolean);
        ArrayList<StoredTrack> tracks = new ArrayList<>();
        if (includeCurrent) {
            StoredTrack.from(manager.getScheduler().getCurrentRequest()).ifPresent(tracks::add);
        }
        manager.getScheduler().queuedRequests().stream()
                .map(StoredTrack::from)
                .flatMap(java.util.Optional::stream)
                .forEach(tracks::add);

        PlaylistOperationResult result = musicLibraryRepository.addTracks(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                administrationPolicy.canManage(event.getMember()),
                tracks);
        replyPlaylistMutation(event, result);
    }

    private void addHistoryTrackToPlaylist(SlashCommandInteractionEvent event, String name) {
        long requestedPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        List<StoredTrack> history = musicLibraryRepository.history(event.getGuild().getIdLong());
        if (requestedPosition < 1L || requestedPosition > history.size()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🕘 Позиция истории не найдена",
                            history.isEmpty()
                                    ? "История пока пуста."
                                    : "Укажи номер из диапазона `1.." + history.size() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        PlaylistOperationResult result = musicLibraryRepository.addTrack(
                event.getGuild().getIdLong(),
                name,
                event.getUser().getIdLong(),
                administrationPolicy.canManage(event.getMember()),
                history.get(Math.toIntExact(requestedPosition - 1L)));
        replyPlaylistMutation(event, result);
    }

    private void searchPlaylists(SlashCommandInteractionEvent event) {
        String query = event.getOption("query", "", OptionMapping::getAsString);
        event.replyEmbeds(MusicEmbeds.playlistSearch(
                        query,
                        musicLibraryRepository.search(event.getGuild().getIdLong(), query)))
                .setEphemeral(true)
                .queue();
    }

    private void deletePlaylist(SlashCommandInteractionEvent event, String name) {
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
        boolean canManage = playlist.ownerUserId() == event.getUser().getIdLong()
                || administrationPolicy.canManage(event.getMember());
        if (!canManage) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔐 Недостаточно прав",
                            "Удалить этот плейлист может его создатель или администратор Баскова."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        requestConfirmation(
                event,
                ConfirmationStore.Action.DELETE_PLAYLIST,
                playlist.name(),
                "🗑️ Удалить плейлист?",
                "Плейлист `" + playlist.name() + "` содержит `" + playlist.tracks().size()
                        + "` треков. Это действие нельзя отменить кнопкой после подтверждения.");
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
            case BULK_ADDED -> MusicEmbeds.success(
                    "📥 Очередь сохранена",
                    "В `" + result.playlist().name() + "` добавлено `" + result.affectedTracks()
                            + "` треков. Всего: `" + result.playlist().tracks().size() + "`.");
            case MOVED -> MusicEmbeds.success(
                    "↕️ Трек перемещён",
                    "`" + result.track().title() + "` перемещён внутри `" + result.playlist().name() + "`.");
            case DEDUPED -> MusicEmbeds.success(
                    "🧹 Дубликаты обработаны",
                    result.affectedTracks() == 0
                            ? "В `" + result.playlist().name() + "` дубликатов не найдено."
                            : "Удалено повторов: `" + result.affectedTracks() + "`. Осталось: `"
                                    + result.playlist().tracks().size() + "`.");
            case RENAMED -> MusicEmbeds.success(
                    "✏️ Плейлист переименован",
                    "Новое название: `" + result.playlist().name() + "`.");
            case COPIED -> MusicEmbeds.success(
                    "📑 Плейлист скопирован",
                    "Создана твоя копия `" + result.playlist().name() + "` с `"
                            + result.playlist().tracks().size() + "` треками.");
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
                .setComponents(MusicControls.nowRows(manager))
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

    private void replyVoteStatus(ButtonInteractionEvent event, Guild guild, Member member) {
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        AudioTrack current = manager == null ? null : manager.getAudioPlayer().getPlayingTrack();
        if (current == null) {
            event.replyEmbeds(MusicEmbeds.error("🗳️ Голосования нет", "Сейчас ничего не играет."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        GuildPreferences preferences = preferencesRepository.get(guild.getIdLong());
        if (preferences.accessMode() != PlaybackAccessMode.VOTE_SKIP) {
            event.replyEmbeds(MusicEmbeds.success(
                            "🗳️ Vote skip не требуется",
                            "Режим управления сервером: `" + preferences.accessMode().label()
                                    + "`. Голосование используется только в режиме `DJ + голосование за пропуск`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        int eligibleListeners = eligibleHumanListeners(guild);
        VoteSkipService.VoteSnapshot snapshot = voteSkipService.snapshot(
                guild.getIdLong(),
                playbackVoteKey(current),
                member.getIdLong(),
                eligibleListeners,
                preferences.voteSkipPercent());
        int remaining = Math.max(0, snapshot.requiredVotes() - snapshot.votes());
        String description = "Текущий трек: `" + current.getInfo().title + "`\n"
                + "Голосов: `" + snapshot.votes() + "/" + snapshot.requiredVotes() + "`\n"
                + "Слушателей: `" + snapshot.eligibleListeners() + "` • порог: `" + snapshot.thresholdPercent() + "%`\n"
                + "Твой голос: `" + (snapshot.viewerVoted() ? "уже учтён" : "ещё не отдан") + "`\n"
                + (remaining == 0 ? "Порог уже достигнут или не требует дополнительных голосов."
                : "До порога осталось: `" + remaining + "`. Нажми `Пропустить` или используй `/voteskip`.");
        event.replyEmbeds(MusicEmbeds.success("🗳️ Vote skip", description))
                .setEphemeral(true)
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
        requestConfirmation(
                event,
                ConfirmationStore.Action.STOP,
                title,
                "⏹️ Остановить музыкальную сессию?",
                "Будет остановлен `" + title + "`, очищена очередь и закрыто voice-соединение.");
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
                .setComponents(MusicControls.nowRows(manager))
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
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        event.replyEmbeds(MusicEmbeds.success(
                        "⏳ Трек перемотан",
                        "Новая позиция: `" + MusicEmbeds.formatTime(clamped) + "`."))
                .setComponents(MusicControls.nowRows(manager))
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

        int queued = manager.getScheduler().queueSize();
        if (queued == 0) {
            event.replyEmbeds(MusicEmbeds.success(
                            "🧹 Очередь уже пуста",
                            "Ожидающих треков нет. Текущая песня продолжает играть."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        requestConfirmation(
                event,
                ConfirmationStore.Action.CLEAR_QUEUE,
                String.valueOf(queued),
                "🧹 Очистить ожидающую очередь?",
                "Будет удалено `" + queued + "` ожидающих треков. Текущая песня продолжит играть.");
    }

    private void queueManage(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "stats".equals(subcommand)) {
            GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
            event.replyEmbeds(MusicEmbeds.queueStats(manager)).setEphemeral(true).queue();
            return;
        }
        if ("mine".equals(subcommand)) {
            GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
            event.replyEmbeds(MusicEmbeds.personalQueue(manager, event.getUser().getIdLong()))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if ("community".equals(subcommand)) {
            GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
            event.replyEmbeds(MusicEmbeds.queueCommunity(manager)).setEphemeral(true).queue();
            return;
        }

        OptionalLong expectedRevision = queueRevisionOption(event);
        if (expectedRevision.isPresent() && expectedRevision.getAsLong() < 0L) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔢 Неверная ревизия",
                            "Ревизия очереди должна быть неотрицательным числом из `/queue` или `/queue-manage stats`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (subcommand) {
            case "remove-own" -> removeOwnQueueEntry(event, expectedRevision);
            case "remove-range" -> removeQueueRange(event, expectedRevision);
            case "dedupe" -> deduplicateQueue(event, expectedRevision);
            case "remove-mine" -> removeOwnQueueEntries(event, expectedRevision);
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "📋 Неизвестная операция",
                            "Используй `/queue-manage stats|mine|community|remove-own|remove-range|dedupe|remove-mine`."))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void removeQueueRange(
            SlashCommandInteractionEvent event,
            OptionalLong expectedRevision) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        long rawStart = event.getOption("start", -1L, OptionMapping::getAsLong);
        long rawEnd = event.getOption("end", -1L, OptionMapping::getAsLong);
        if (rawStart < 1L || rawEnd < rawStart
                || rawStart > Integer.MAX_VALUE || rawEnd > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🗑️ Неверный диапазон",
                            "Укажи позиции `start..end`, где обе позиции существуют в текущей очереди."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackScheduler.QueueMutationResult result = manager.getScheduler().removeRange(
                Math.toIntExact(rawStart),
                Math.toIntExact(rawEnd),
                expectedRevision);
        if (!replyQueueMutationFailure(event, result)) {
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "🗑️ Диапазон удалён",
                        "Удалено треков: `" + result.removedCount() + "`\n"
                                + "Освобождено времени: `"
                                + MusicEmbeds.humanMillis(result.removedDurationMillis()) + "`\n"
                                + "Осталось в очереди: `" + result.queueSize() + "`\n"
                                + "Новая ревизия: `" + result.revision() + "`."))
                .queue();
    }

    private void deduplicateQueue(
            SlashCommandInteractionEvent event,
            OptionalLong expectedRevision) {
        if (!allowControl(event)) {
            return;
        }
        GuildMusicManager manager = activeManager(event);
        if (manager == null) {
            return;
        }

        TrackScheduler.QueueMutationResult result = manager.getScheduler()
                .deduplicateQueue(expectedRevision);
        if (result.status() == TrackScheduler.QueueMutationStatus.NO_CHANGES) {
            event.replyEmbeds(MusicEmbeds.success(
                            "✨ Очередь уже чистая",
                            "Повторных ожидающих треков не найдено. Ревизия: `"
                                    + result.revision() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!replyQueueMutationFailure(event, result)) {
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "✨ Дубликаты удалены",
                        "Удалено повторов: `" + result.removedCount() + "`\n"
                                + "Освобождено времени: `"
                                + MusicEmbeds.humanMillis(result.removedDurationMillis()) + "`\n"
                                + "Осталось в очереди: `" + result.queueSize() + "`\n"
                                + "Новая ревизия: `" + result.revision() + "`."))
                .queue();
    }

    private void removeOwnQueueEntry(
            SlashCommandInteractionEvent event,
            OptionalLong expectedRevision) {
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error("🎵 Музыкальной сессии нет", "Сейчас нет ожидающей очереди."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        long rawPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        if (rawPosition < 1L || rawPosition > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error("🗑️ Неверная позиция", "Укажи глобальную позицию из `/queue`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackScheduler.QueueMutationResult result = manager.getScheduler().removeRequesterAt(
                event.getUser().getIdLong(),
                Math.toIntExact(rawPosition),
                expectedRevision);
        if (result.status() == TrackScheduler.QueueMutationStatus.NOT_OWNER) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔐 Это чужой трек",
                            "Без DJ/manager прав через `remove-own` можно удалить только трек, заказанный тобой. "
                                    + "Открой `/queue-manage mine` для своих позиций."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!replyQueueMutationFailure(event, result)) {
            return;
        }
        TrackRequest removed = result.removed().get(0);
        event.replyEmbeds(MusicEmbeds.success(
                        "🗑️ Твой трек удалён",
                        "Удалён `" + removed.track().getInfo().title + "`.\n"
                                + "Осталось в очереди: `" + result.queueSize() + "`\n"
                                + "Новая ревизия: `" + result.revision() + "`."))
                .setEphemeral(true)
                .queue();
    }

    private void removeOwnQueueEntries(
            SlashCommandInteractionEvent event,
            OptionalLong expectedRevision) {
        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎵 Музыкальной сессии нет",
                            "Сейчас нет ожидающей очереди."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackScheduler.QueueMutationResult result = manager.getScheduler().removeRequester(
                event.getUser().getIdLong(),
                expectedRevision);
        if (result.status() == TrackScheduler.QueueMutationStatus.NO_CHANGES) {
            event.replyEmbeds(MusicEmbeds.success(
                            "🧹 Твоих треков нет",
                            "В ожидающей очереди нет треков, добавленных тобой. Ревизия: `"
                                    + result.revision() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!replyQueueMutationFailure(event, result)) {
            return;
        }
        event.replyEmbeds(MusicEmbeds.success(
                        "🧹 Твои треки удалены",
                        "Удалено твоих ожидающих треков: `" + result.removedCount() + "`\n"
                                + "Освобождено времени: `"
                                + MusicEmbeds.humanMillis(result.removedDurationMillis()) + "`\n"
                                + "Осталось в очереди: `" + result.queueSize() + "`\n"
                                + "Новая ревизия: `" + result.revision() + "`."))
                .setEphemeral(true)
                .queue();
    }

    private boolean replyQueueMutationFailure(
            SlashCommandInteractionEvent event,
            TrackScheduler.QueueMutationResult result) {
        if (result.status() == TrackScheduler.QueueMutationStatus.APPLIED) {
            return true;
        }
        if (result.status() == TrackScheduler.QueueMutationStatus.STALE_REVISION) {
            event.replyEmbeds(MusicEmbeds.error(
                            "♻️ Очередь уже изменилась",
                            "Переданная ревизия устарела. Текущая ревизия: `" + result.revision()
                                    + "`. Обнови `/queue` и повтори команду."))
                    .setEphemeral(true)
                    .queue();
            return false;
        }
        if (result.status() == TrackScheduler.QueueMutationStatus.INVALID_ARGUMENT) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📋 Операция не выполнена",
                            "Позиции больше не существуют или параметры команды некорректны. "
                                    + "Сейчас в очереди `" + result.queueSize() + "` треков."))
                    .setEphemeral(true)
                    .queue();
            return false;
        }
        return false;
    }

    private static OptionalLong queueRevisionOption(SlashCommandInteractionEvent event) {
        OptionMapping revision = event.getOption("revision");
        return revision == null ? OptionalLong.empty() : OptionalLong.of(revision.getAsLong());
    }

    private void moderation(SlashCommandInteractionEvent event) {
        if (!allowModeration(event)) {
            return;
        }
        String subcommand = event.getSubcommandName();
        if (subcommand == null || "status".equals(subcommand)) {
            event.replyEmbeds(moderationStatusEmbed(event.getGuild())).setEphemeral(true).queue();
            return;
        }
        if ("audit".equals(subcommand)) {
            showAdministrativeAudit(event);
            return;
        }

        OptionalLong expectedRevision = queueRevisionOption(event);
        if (expectedRevision.isPresent() && expectedRevision.getAsLong() < 0L) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔢 Неверная ревизия",
                            "Ревизия очереди должна быть неотрицательным числом из `/queue`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildMusicManager manager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        if (manager == null) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎵 Музыкальной сессии нет",
                            "Сейчас нет активной очереди для moderation."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (subcommand) {
            case "remove" -> moderationRemove(event, manager, expectedRevision);
            case "purge" -> moderationPurge(event, manager, expectedRevision);
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "🛡️ Неизвестная moderation-команда",
                            "Используй `/moderation status|remove|purge|audit`."))
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void moderationRemove(
            SlashCommandInteractionEvent event,
            GuildMusicManager manager,
            OptionalLong expectedRevision) {
        long rawPosition = event.getOption("position", -1L, OptionMapping::getAsLong);
        if (rawPosition < 1L || rawPosition > Integer.MAX_VALUE) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🗑️ Неверная позиция",
                            "Укажи глобальную позицию из `/queue`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackScheduler.QueueMutationResult result = manager.getScheduler().removeRange(
                Math.toIntExact(rawPosition),
                Math.toIntExact(rawPosition),
                expectedRevision);
        if (!replyQueueMutationFailure(event, result)) {
            return;
        }
        TrackRequest removed = result.removed().get(0);
        recordSettingsAudit(event, "moderation:remove position=" + rawPosition
                + " requester=" + removed.requester().userId());
        event.replyEmbeds(MusicEmbeds.success(
                        "🛡️ Трек удалён модератором",
                        "Удалён: `" + removed.track().getInfo().title + "`\n"
                                + "Заказал: " + removed.requester().discordLabel() + "\n"
                                + "Новая ревизия: `" + result.revision() + "`."))
                .setEphemeral(true)
                .queue();
    }

    private void moderationPurge(
            SlashCommandInteractionEvent event,
            GuildMusicManager manager,
            OptionalLong expectedRevision) {
        long targetUserId = event.getOption(
                "user",
                0L,
                option -> option.getAsUser().getIdLong());
        if (targetUserId <= 0L) {
            event.replyEmbeds(MusicEmbeds.error(
                            "👤 Пользователь не указан",
                            "Выбери пользователя, чьи ожидающие треки нужно удалить."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TrackScheduler.QueueMutationResult result = manager.getScheduler().removeRequester(
                targetUserId,
                expectedRevision);
        if (result.status() == TrackScheduler.QueueMutationStatus.NO_CHANGES) {
            event.replyEmbeds(MusicEmbeds.success(
                            "🛡️ Очищать нечего",
                            "У <@" + targetUserId + "> нет ожидающих треков. Ревизия: `"
                                    + result.revision() + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!replyQueueMutationFailure(event, result)) {
            return;
        }
        recordSettingsAudit(event, "moderation:purge requester=" + targetUserId
                + " removed=" + result.removedCount());
        event.replyEmbeds(MusicEmbeds.success(
                        "🛡️ Очередь пользователя очищена",
                        "Удалено у <@" + targetUserId + ">: `" + result.removedCount() + "` треков\n"
                                + "Освобождено времени: `"
                                + MusicEmbeds.humanMillis(result.removedDurationMillis()) + "`\n"
                                + "Новая ревизия: `" + result.revision() + "`."))
                .setEphemeral(true)
                .queue();
    }

    private MessageEmbed moderationStatusEmbed(Guild guild) {
        GuildPreferences preferences = preferencesRepository.get(guild.getIdLong());
        GuildMusicManager manager = playerManager.findMusicManager(guild).orElse(null);
        TrackScheduler.QueueStats stats = manager == null
                ? new TrackScheduler.QueueStats(0L, 0, 0L, 0, 0)
                : manager.getScheduler().queueStats();
        return new EmbedBuilder()
                .setTitle("🛡️ Queue moderation")
                .setColor(Color.ORANGE)
                .addField("Moderator-role", moderatorRoleLabel(preferences), true)
                .addField("DJ-role", djRoleLabel(preferences), true)
                .addField("Manager-role", managerRoleLabel(preferences), true)
                .addField("Pending / user", requesterQueueLimitLabel(preferences), true)
                .addField("Очередь", "`" + stats.size() + "` треков", true)
                .addField("Revision", "`" + stats.revision() + "`", true)
                .addField("Заказчиков", "`" + stats.uniqueRequesters() + "`", true)
                .setFooter("remove/purge используют queue revision; moderator-role не может менять settings")
                .build();
    }

    private boolean allowModeration(SlashCommandInteractionEvent event) {
        if (moderationPolicy.canModerate(event.getMember())) {
            return true;
        }
        event.replyEmbeds(MusicEmbeds.error(
                        "🔐 Moderation недоступна",
                        "Очередь может модерировать владелец, `Manage Server`, manager-role, moderator-role или DJ-role."))
                .setEphemeral(true)
                .queue();
        return false;
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
            case "request-access" -> updateRequestAccessMode(event);
            case "dj-role" -> updateDjRole(event);
            case "manager-role" -> updateManagerRole(event);
            case "moderator-role" -> updateModeratorRole(event);
            case "requester-limit" -> updateRequesterQueueLimit(event);
            case "voice-channel" -> updateMusicVoiceChannel(event);
            case "vote-threshold" -> updateVoteThreshold(event);
            case "permissions" -> event.replyEmbeds(permissionsEmbed(
                            preferencesRepository.get(event.getGuild().getIdLong())))
                    .setEphemeral(true)
                    .queue();
            case "audit" -> showAdministrativeAudit(event);
            case "export" -> exportSettings(event);
            case "import" -> importSettings(event);
            case "reset" -> resetSettings(event);
            default -> event.replyEmbeds(MusicEmbeds.error(
                            "⚙️ Неизвестная настройка",
                            "Используй `/settings show` или `/help`, чтобы увидеть доступные административные команды."))
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
        recordSettingsAudit(event, "volume=" + volume);
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
        recordSettingsAudit(event, "repeat=" + mode.name());
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
        recordSettingsAudit(event, "playback-access=" + mode.name());
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateRequestAccessMode(SlashCommandInteractionEvent event) {
        String rawMode = event.getOption("mode", "open", OptionMapping::getAsString);
        RequestAccessMode mode;
        try {
            mode = RequestAccessMode.parse(rawMode);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎼 Неизвестный режим запросов",
                            "Доступны режимы: `open` и `dj`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveRequestAccessMode(
                event.getGuild().getIdLong(), mode);
        recordSettingsAudit(event, "request-access=" + mode.name());
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateDjRole(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", null, OptionMapping::getAsRole);
        if (!validateAdministrativeRole(event, role, "DJ")) {
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveDjRoleId(
                event.getGuild().getIdLong(), role == null ? 0L : role.getIdLong());
        voteSkipService.reset(event.getGuild().getIdLong());
        recordSettingsAudit(event, "dj-role=" + (role == null ? "cleared" : role.getId()));
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateManagerRole(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", null, OptionMapping::getAsRole);
        if (!validateAdministrativeRole(event, role, "manager")) {
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveManagerRoleId(
                event.getGuild().getIdLong(), role == null ? 0L : role.getIdLong());
        recordSettingsAudit(event, "manager-role=" + (role == null ? "cleared" : role.getId()));
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private boolean validateAdministrativeRole(SlashCommandInteractionEvent event, Role role, String label) {
        if (role == null) {
            return true;
        }
        if (role.isPublicRole()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🎧 Нельзя использовать @everyone",
                            "Выбери отдельную " + label + "-роль или оставь поле пустым, чтобы очистить настройку."))
                    .setEphemeral(true)
                    .queue();
            return false;
        }
        return true;
    }

    private void updateModeratorRole(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", null, OptionMapping::getAsRole);
        if (!validateAdministrativeRole(event, role, "moderator")) {
            return;
        }

        GuildPreferences preferences = preferencesRepository.saveModeratorRoleId(
                event.getGuild().getIdLong(), role == null ? 0L : role.getIdLong());
        recordSettingsAudit(event, "moderator-role=" + (role == null ? "cleared" : role.getId()));
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateRequesterQueueLimit(SlashCommandInteractionEvent event) {
        long requested = event.getOption("max", -1L, OptionMapping::getAsLong);
        int maximum = Math.min(100, musicProperties.getMaxQueueSize());
        if (requested < 0 || requested > maximum) {
            event.replyEmbeds(MusicEmbeds.error(
                            "👤 Недопустимый персональный лимит",
                            "Укажи `0` для отключения лимита или значение от `1` до `"
                                    + maximum + "`."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int limit = Math.toIntExact(requested);
        GuildPreferences preferences = preferencesRepository.saveRequesterQueueLimit(
                event.getGuild().getIdLong(), limit);
        playerManager.findMusicManager(event.getGuild())
                .ifPresent(manager -> manager.getScheduler().setRequesterQueueLimit(limit));
        recordSettingsAudit(event, "requester-queue-limit=" + limit);
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void updateMusicVoiceChannel(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("channel");
        GuildChannel channel = option == null ? null : option.getAsChannel();
        if (channel != null && !channel.getType().isAudio()) {
            event.replyEmbeds(MusicEmbeds.error(
                            "🔊 Нужен голосовой канал",
                            "Ограничение можно назначить только на voice или stage канал."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long channelId = channel == null ? 0L : channel.getIdLong();
        GuildPreferences preferences = preferencesRepository.saveMusicChannelId(
                event.getGuild().getIdLong(), channelId);
        recordSettingsAudit(event, "music-channel=" + (channel == null ? "cleared" : channel.getId()));
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
        recordSettingsAudit(event, "vote-threshold=" + requested);
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void exportSettings(SlashCommandInteractionEvent event) {
        GuildPreferences preferences = preferencesRepository.get(event.getGuild().getIdLong());
        event.reply("```text\n" + SettingsProfileCodec.encode(preferences) + "\n```")
                .setEphemeral(true)
                .queue();
    }

    private void importSettings(SlashCommandInteractionEvent event) {
        String rawProfile = event.getOption("profile", "", OptionMapping::getAsString).trim();
        final GuildPreferences imported;
        try {
            imported = SettingsProfileCodec.decode(rawProfile);
            validateImportedSettings(event.getGuild(), imported);
        } catch (IllegalArgumentException exception) {
            event.replyEmbeds(MusicEmbeds.error(
                            "📦 Профиль настроек отклонён",
                            exception.getMessage()))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildPreferences preferences = preferencesRepository.replace(event.getGuild().getIdLong(), imported);
        voteSkipService.reset(event.getGuild().getIdLong());
        playerManager.findMusicManager(event.getGuild()).ifPresent(manager -> {
            manager.getAudioPlayer().setVolume(preferences.volume());
            manager.getScheduler().setRepeatMode(preferences.repeatMode());
            manager.getScheduler().setRequesterQueueLimit(preferences.requesterQueueLimit());
        });
        recordSettingsAudit(event, "profile-import");
        event.replyEmbeds(settingsEmbed(preferences)).setEphemeral(true).queue();
    }

    private void validateImportedSettings(Guild guild, GuildPreferences preferences) {
        if (preferences.volume() > musicProperties.getMaxVolume()) {
            throw new IllegalArgumentException("Громкость профиля превышает допустимый максимум этого бота.");
        }
        validateImportedRole(guild, preferences.djRoleId(), "DJ-роль");
        validateImportedRole(guild, preferences.managerRoleId(), "manager-role");
        validateImportedRole(guild, preferences.moderatorRoleId(), "moderator-role");
        if (preferences.requesterQueueLimit() > musicProperties.getMaxQueueSize()) {
            throw new IllegalArgumentException("Персональный лимит очереди профиля превышает max queue size этого бота.");
        }
        if (preferences.musicChannelId() > 0) {
            GuildChannel channel = guild.getGuildChannelById(preferences.musicChannelId());
            if (channel == null || !channel.getType().isAudio()) {
                throw new IllegalArgumentException("Voice/stage канал из профиля не существует на этом сервере.");
            }
        }
    }

    private static void validateImportedRole(Guild guild, long roleId, String label) {
        if (roleId <= 0) {
            return;
        }
        Role role = guild.getRoleById(roleId);
        if (role == null || role.isPublicRole()) {
            throw new IllegalArgumentException(label + " из профиля не существует на этом сервере.");
        }
    }

    private void showAdministrativeAudit(SlashCommandInteractionEvent event) {
        List<GuildSettingsAuditEntry> entries = preferencesRepository.recentAudit(event.getGuild().getIdLong());
        if (entries.isEmpty()) {
            event.replyEmbeds(MusicEmbeds.success(
                            "🧾 Administrative audit",
                            "Сохраняемых administrative/moderation действий ещё нет. Новые записи будут храниться в последних 25 событиях."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
                .withZone(ZoneId.systemDefault());
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (GuildSettingsAuditEntry entry : entries) {
            lines.add("`" + index++ + ".` " + formatter.format(entry.occurredAt())
                    + " • <@" + entry.actorUserId() + "> • `" + entry.action() + "`");
        }
        event.replyEmbeds(new EmbedBuilder()
                        .setTitle("🧾 Administrative & moderation audit")
                        .setDescription(String.join("\n", lines))
                        .setColor(Color.ORANGE)
                        .setFooter("Хранятся последние 25 administrative/moderation действий в guild-settings.properties")
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private void resetSettings(SlashCommandInteractionEvent event) {
        GuildPreferences current = preferencesRepository.get(event.getGuild().getIdLong());
        requestConfirmation(
                event,
                ConfirmationStore.Action.RESET_SETTINGS,
                "",
                "⚠️ Сбросить guild settings?",
                "Будут удалены overrides громкости, repeat/access policies, DJ/manager/moderator roles, "
                        + "requester queue limit, music-channel restriction и vote-skip threshold. Текущая громкость: `"
                        + current.volume() + "%`.");
    }

    private void requestConfirmation(
            SlashCommandInteractionEvent event,
            ConfirmationStore.Action action,
            String payload,
            String title,
            String description) {
        ConfirmationStore.PendingConfirmation confirmation = confirmationStore.create(
                action,
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                payload);
        event.replyEmbeds(confirmationEmbed(title, description, confirmation))
                .setComponents(ExperienceControls.confirmationRows(confirmation.token()))
                .setEphemeral(true)
                .queue();
    }

    private void requestConfirmation(
            ButtonInteractionEvent event,
            ConfirmationStore.Action action,
            String payload,
            String title,
            String description) {
        ConfirmationStore.PendingConfirmation confirmation = confirmationStore.create(
                action,
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                payload);
        event.replyEmbeds(confirmationEmbed(title, description, confirmation))
                .setComponents(ExperienceControls.confirmationRows(confirmation.token()))
                .setEphemeral(true)
                .queue();
    }

    private static MessageEmbed confirmationEmbed(
            String title,
            String description,
            ConfirmationStore.PendingConfirmation confirmation) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description
                        + "\n\nПодтверждение одноразовое и истекает <t:"
                        + confirmation.expiresAt().getEpochSecond() + ":R>.")
                .setColor(Color.ORANGE)
                .setFooter("Нажми «Подтвердить» или «Отмена» — повторный клик ничего не выполнит")
                .build();
    }

    private void recordSettingsAudit(SlashCommandInteractionEvent event, String action) {
        recordSettingsAudit(event.getGuild(), event.getUser().getIdLong(), action);
    }

    private void recordSettingsAudit(Guild guild, long userId, String action) {
        try {
            preferencesRepository.recordAudit(guild.getIdLong(), userId, action);
        } catch (RuntimeException exception) {
            log.error("Cannot persist guild settings audit for guild {} action {}",
                    guild.getId(), action, exception);
        }
    }

    private boolean allowManageSettings(SlashCommandInteractionEvent event) {
        if (administrationPolicy.canManage(event.getMember())) {
            return true;
        }
        event.replyEmbeds(MusicEmbeds.error(
                        "🔐 Недостаточно прав",
                        "Изменять guild settings может владелец, участник с `Manage Server` "
                                + "или настроенной manager-role Баскова."))
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
                .addField("Управление playback", "`" + preferences.accessMode().label() + "`", true)
                .addField("Добавление музыки", "`" + preferences.requestAccessMode().label() + "`", true)
                .addField("DJ-роль", djRoleLabel(preferences), true)
                .addField("Manager-role", managerRoleLabel(preferences), true)
                .addField("Moderator-role", moderatorRoleLabel(preferences), true)
                .addField("Лимит очереди / user", requesterQueueLimitLabel(preferences), true)
                .addField("Музыкальный канал", musicChannelLabel(preferences), true)
                .addField("Порог vote-skip", "`" + preferences.voteSkipPercent() + "%`", true)
                .setFooter("/settings permissions — матрица доступа; /settings audit — последние изменения")
                .build();
    }

    private MessageEmbed permissionsEmbed(GuildPreferences preferences) {
        return new EmbedBuilder()
                .setTitle("🔐 Матрица доступа Баскова")
                .setColor(Color.ORANGE)
                .addField("Администрирование",
                        "Владелец / `Manage Server` / " + managerRoleLabel(preferences), false)
                .addField("Добавление треков и поиск",
                        "`" + preferences.requestAccessMode().label() + "`"
                                + (preferences.hasMusicChannel()
                                ? " • только <#" + preferences.musicChannelId() + ">" : ""), false)
                .addField("Playback controls", "`" + preferences.accessMode().label() + "`", false)
                .addField("Queue moderation", "Владелец / `Manage Server` / "
                        + managerRoleLabel(preferences) + " / " + moderatorRoleLabel(preferences)
                        + " / " + djRoleLabel(preferences), false)
                .addField("Лимит pending / user", requesterQueueLimitLabel(preferences), true)
                .addField("Vote-skip", "`" + preferences.voteSkipPercent() + "%`", true)
                .setFooter("Moderator-role модерирует очередь, но не изменяет guild settings")
                .build();
    }

    private static String djRoleLabel(GuildPreferences preferences) {
        return preferences.hasDjRole() ? "<@&" + preferences.djRoleId() + ">" : "`не назначена`";
    }

    private static String managerRoleLabel(GuildPreferences preferences) {
        return preferences.hasManagerRole() ? "<@&" + preferences.managerRoleId() + ">" : "`не назначена`";
    }

    private static String moderatorRoleLabel(GuildPreferences preferences) {
        return preferences.hasModeratorRole()
                ? "<@&" + preferences.moderatorRoleId() + ">"
                : "`не назначена`";
    }

    private static String requesterQueueLimitLabel(GuildPreferences preferences) {
        return preferences.hasRequesterQueueLimit()
                ? "`" + preferences.requesterQueueLimit() + " pending`"
                : "`без персонального лимита`";
    }

    private static String musicChannelLabel(GuildPreferences preferences) {
        return preferences.hasMusicChannel() ? "<#" + preferences.musicChannelId() + ">" : "`без ограничения`";
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
        String title = player.getPlayingTrack().getInfo().title;
        requestConfirmation(
                event,
                ConfirmationStore.Action.STOP,
                title,
                "⏹️ Остановить музыкальную сессию?",
                "Будет остановлен `" + title + "`, очищена очередь и закрыто voice-соединение.");
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

    private MessageEmbed helpEmbed(ExperienceControls.HelpSection section, Member member) {
        ExperienceControls.HelpSection safeSection = section == null
                ? ExperienceControls.HelpSection.OVERVIEW
                : section;
        GuildPreferences preferences = preferencesRepository.get(member.getGuild().getIdLong());
        boolean canManage = administrationPolicy.canManage(member);
        boolean canModerate = moderationPolicy.canModerate(member);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.CYAN)
                .setFooter("Кнопки ниже переключают разделы без новой slash-команды");

        switch (safeSection) {
            case OVERVIEW -> embed
                    .setTitle("🎤 Басков • быстрый обзор")
                    .setDescription("Slash-команды — основной интерфейс. Выбери раздел кнопками ниже.")
                    .addField("▶️ Быстрый старт", "`/play` → `/now` → `/queue`", false)
                    .addField("🔎 Найти трек", "`/search` показывает до пяти вариантов; `/discover` продолжает знакомый поиск.", false)
                    .addField("📚 Сохранить музыку", "`/favorites`, `/playlist` и `/history` переживают restart контейнера.", false)
                    .addField("🩺 Проверить сервис", "`/status` и `/session status` показывают live health и playback recovery.", false)
                    .addField("Твои права здесь", canManage
                            ? "`admin` — можно менять guild settings и административно управлять библиотекой."
                            : "`listener` — административные действия зависят от ролей сервера.", false)
                    .addField("Политики сервера", "Requests: `" + preferences.requestAccessMode().label()
                            + "` • Playback: `" + preferences.accessMode().label() + "`", false);
            case PLAYBACK -> embed
                    .setTitle("▶️ Воспроизведение")
                    .setDescription("Команды активной музыкальной сессии.")
                    .addField("Запуск", "`/play` `/search` `/discover` `/replay` `/favorites play|play-all` `/playlist play`", false)
                    .addField("Управление", "`/pause` `/resume` `/previous` `/skip` `/voteskip` `/seek` `/volume` `/repeat`", false)
                    .addField("Пульт `/now`", "Предыдущий, ±15 секунд, pause/resume, skip, shuffle, repeat, очередь, refresh и stop.", false)
                    .addField("Защита stop", "`/stop` и кнопка Stop сначала показывают одноразовое подтверждение на 2 минуты.", false)
                    .addField("Voice policy", "Добавление музыки: `" + preferences.requestAccessMode().label()
                            + "` • Music channel: " + musicChannelLabel(preferences), false);
            case QUEUE -> embed
                    .setTitle("📋 Очередь")
                    .setDescription("Просмотр и безопасное изменение ожидающих треков.")
                    .addField("Основное", "`/queue` `/remove` `/move` `/shuffle` `/clear`", false)
                    .addField("Queue Manager", "`stats` `mine` `community` `remove-own` `remove-range` `dedupe` `remove-mine`", false)
                    .addField("Совместная очередь", "В `/queue` есть кнопки `Мои треки`, `Заказчики` и read-only статус vote-skip.", false)
                    .addField("Moderation", canModerate
                            ? "`/moderation status|remove|purge|audit` — у тебя есть queue moderation access."
                            : "Queue moderation требует DJ/moderator/manager либо Discord admin rights.", false)
                    .addField("Ревизии", "Позиционные операции могут сверять revision, чтобы не удалить уже сдвинувшийся или чужой трек.", false)
                    .addField("Защита clear", "Непустая `/clear` теперь требует отдельной кнопки подтверждения; текущий трек не останавливается.", false);
            case LIBRARY -> embed
                    .setTitle("📚 Библиотека и discovery")
                    .setDescription("Постоянные плейлисты, история и повторное воспроизведение.")
                    .addField("Избранное", "`/favorites list|add|play|play-all|remove|search|clear` — личное для каждого пользователя.", false)
                    .addField("Плейлисты", "`/playlist list|create|show|add|play|remove|move|rename|copy|dedupe|capture-queue|add-history|search|delete`", false)
                    .addField("История", "`/history scope:server|mine` `/replay scope:server|mine` `/discover history`", false)
                    .addField("Discovery", "`/discover recent|again|related|history|profile|for-me`", false)
                    .addField("Безопасное удаление", "`/playlist delete` и `/favorites clear` защищены одноразовым интерактивным подтверждением.", false)
                    .addField("Autocomplete", "`/play`, `/search` учитывают твоё избранное, history и playlists; playlist names тоже дополняются локально без сетевого запроса на каждый символ.", false);
            case ADMIN -> embed
                    .setTitle("⚙️ Guild Administration")
                    .setDescription(canManage
                            ? "У тебя есть административный доступ Баскова на этом сервере."
                            : "Для изменения настроек нужны owner, `Manage Server` или manager-role Баскова.")
                    .addField("Посмотреть", "`/settings show` `/settings permissions` `/settings audit`", false)
                    .addField("Доступ", "`/settings access` `/settings request-access` `/settings dj-role` `/settings manager-role` `/settings moderator-role` `/settings voice-channel`", false)
                    .addField("Moderation", "`/settings requester-limit` + `/moderation status|remove|purge|audit`", false)
                    .addField("Поведение", "`/settings volume` `/settings repeat` `/settings vote-threshold`", false)
                    .addField("Перенос", "`/settings export` `/settings import`", false)
                    .addField("Сброс", "`/settings reset` открывает интерактивное подтверждение; `confirm:true` больше вводить не нужно.", false)
                    .addField("Диагностика", "`/doctor summary|gateway|voice|storage|session|source|failures` — actionable diagnosis; `/status` — raw snapshot; `/session status|recover` — checkpoint/recovery.", false)
                    .addField("Smart radio", "`/radio start|status|stop` — bounded autoplay из personal/server history без внешнего recommendation service.", false);
        }
        return embed.build();
    }

}
