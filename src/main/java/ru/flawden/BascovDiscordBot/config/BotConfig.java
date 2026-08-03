package ru.flawden.BascovDiscordBot.config;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Path READY_FILE = Path.of(System.getProperty("java.io.tmpdir"),
            "baskov-discord-bot.ready");

    private static final EnumSet<GatewayIntent> REQUIRED_INTENTS = EnumSet.of(
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_VOICE_STATES
    );

    private final List<Event> events;
    private final String token;
    private final String prefix;
    private final EventJoin eventJoin;
    private final HelpEvent helpCommand;

    public BotConfig(List<Event> events, Environment env, EventJoin eventJoin, HelpEvent helpCommand) {
        this.events = List.copyOf(events);
        this.token = env.getProperty("discordBot.token", "");
        this.prefix = env.getProperty("discordBot.prefix", "!");
        this.eventJoin = eventJoin;
        this.helpCommand = helpCommand;
        log.info("BotConfig initialized: token={}, prefix='{}', commands={}",
                token.isBlank() ? "missing" : "present", prefix, events.size());
    }

    @Bean
    public CommandCooldowns commandCooldowns() {
        return new CommandCooldowns(Clock.systemUTC());
    }

    @Bean
    public BotEvents createCommand(CommandCooldowns commandCooldowns) {
        BotEvents botEvents = new BotEvents(prefix, events, commandCooldowns);
        helpCommand.setEvents(botEvents.getCommands());
        return botEvents;
    }

    @Bean(destroyMethod = "shutdown")
    public JDA createBot(BotEvents botEvents) {
        try {
            Files.deleteIfExists(READY_FILE);
            if (token.isBlank()) {
                throw new IllegalStateException("DISCORD_BOT_TOKEN is not configured");
            }

            JDA jda = JDABuilder.create(token, REQUIRED_INTENTS)
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("золотые чаши"))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(eventJoin, botEvents)
                    .build()
                    .awaitReady();

            Files.writeString(READY_FILE, jda.getSelfUser().getId());
            log.info("JDA is ready: status={}, guilds={}", jda.getStatus(), jda.getGuilds().size());
            return jda;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discord bot startup was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            log.error("Failed to start JDA", exception);
            throw new IllegalStateException("Discord bot failed to start", exception);
        }
    }
}
