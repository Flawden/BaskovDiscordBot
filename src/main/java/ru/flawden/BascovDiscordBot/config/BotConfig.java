package ru.flawden.BascovDiscordBot.config;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ru.flawden.BascovDiscordBot.commands.HelpEvent;
import ru.flawden.BascovDiscordBot.config.eventconfig.BotEvents;
import ru.flawden.BascovDiscordBot.config.eventconfig.CommandCooldowns;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.events.EventJoin;
import ru.flawden.BascovDiscordBot.events.SelfVoiceStateEvents;
import ru.flawden.BascovDiscordBot.dave.DaveRuntimeInfo;
import ru.flawden.BascovDiscordBot.dave.NativeDaveBootstrap;
import ru.flawden.BascovDiscordBot.interactions.ModernCommandCatalog;
import ru.flawden.BascovDiscordBot.interactions.ModernInteractions;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.operations.JdaRuntimeInfo;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.PersistenceReadiness;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

/**
 * Конфигурация Discord/JDA и реестра префиксных команд.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "discordBot.enabled", havingValue = "true", matchIfMissing = true)
public class BotConfig {

    private static final EnumSet<GatewayIntent> REQUIRED_INTENTS = EnumSet.of(
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_VOICE_STATES
    );

    private final List<Event> events;
    private final String token;
    private final String prefix;
    private final EventJoin eventJoin;
    private final SelfVoiceStateEvents selfVoiceStateEvents;
    private final HelpEvent helpCommand;
    private final ModernInteractions modernInteractions;
    private final OperationalMetrics operationalMetrics;
    private final RuntimeHealthMonitor healthMonitor;
    private final PersistenceReadiness persistenceReadiness;
    private final DaveRuntimeInfo daveRuntimeInfo;
    private final PlayerManager playerManager;

    public BotConfig(
            List<Event> events,
            Environment env,
            EventJoin eventJoin,
            SelfVoiceStateEvents selfVoiceStateEvents,
            HelpEvent helpCommand,
            ModernInteractions modernInteractions,
            OperationalMetrics operationalMetrics,
            RuntimeHealthMonitor healthMonitor,
            PersistenceReadiness persistenceReadiness,
            DaveRuntimeInfo daveRuntimeInfo,
            PlayerManager playerManager) {
        this.events = List.copyOf(events);
        this.token = env.getProperty("discordBot.token", "");
        this.prefix = env.getProperty("discordBot.prefix", "!");
        this.eventJoin = eventJoin;
        this.selfVoiceStateEvents = selfVoiceStateEvents;
        this.helpCommand = helpCommand;
        this.modernInteractions = modernInteractions;
        this.operationalMetrics = operationalMetrics;
        this.healthMonitor = healthMonitor;
        this.persistenceReadiness = persistenceReadiness;
        this.daveRuntimeInfo = daveRuntimeInfo;
        this.playerManager = playerManager;
        log.info("BotConfig initialized: token={}, prefix='{}', commands={}",
                token.isBlank() ? "missing" : "present", prefix, events.size());
    }

    @Bean
    public CommandCooldowns commandCooldowns() {
        return new CommandCooldowns(Clock.systemUTC());
    }

    @Bean
    public BotEvents createCommand(CommandCooldowns commandCooldowns) {
        BotEvents botEvents = new BotEvents(prefix, events, commandCooldowns, operationalMetrics);
        helpCommand.setEvents(botEvents.getCommands());
        return botEvents;
    }

    @Bean(destroyMethod = "shutdown")
    public JDA createBot(BotEvents botEvents) {
        try {
            if (token.isBlank()) {
                throw new IllegalStateException("DISCORD_BOT_TOKEN is not configured");
            }

            persistenceReadiness.requireReady();

            AudioModuleConfig audioModuleConfig =
                    NativeDaveBootstrap.createAudioModuleConfig(daveRuntimeInfo);

            JDA jda = JDABuilder.create(token, REQUIRED_INTENTS)
                    .setAudioModuleConfig(audioModuleConfig)
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("золотые чаши"))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(eventJoin, selfVoiceStateEvents, botEvents, modernInteractions)
                    .build()
                    .awaitReady();

            int registeredCommands = jda.updateCommands()
                    .addCommands(ModernCommandCatalog.commands())
                    .complete()
                    .size();
            healthMonitor.start(jda, registeredCommands);
            playerManager.restorePersistedSessions(jda);
            log.info("JDA is ready: version={}, status={}, guilds={}, slashCommands={}",
                    JdaRuntimeInfo.version(),
                    jda.getStatus(),
                    jda.getGuilds().size(),
                    registeredCommands);
            return jda;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discord bot startup was interrupted", exception);
        } catch (RuntimeException exception) {
            log.error("Failed to start JDA", exception);
            throw new IllegalStateException("Discord bot failed to start", exception);
        }
    }
}
