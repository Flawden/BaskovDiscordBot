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
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import ru.flawden.BascovDiscordBot.commands.HelpEvent;
import ru.flawden.BascovDiscordBot.config.eventconfig.BotEvents;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.events.EventJoin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Конфигурационный класс для настройки бота Discord.
 * Этот класс создает и настраивает необходимые компоненты бота, включая обработчики событий
 * и экземпляр JDA, а также управляет токеном бота и его статусом.
 *
 * @author Flawden
 * @version 1.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "discordBot.enabled", havingValue = "true", matchIfMissing = true)
@PropertySource("classpath:application.properties")
public class BotConfig {

    private static final Path READY_FILE = Path.of(System.getProperty("java.io.tmpdir"),
            "baskov-discord-bot.ready");

    private final List<Event> events;
    private final Environment env;
    private final String token;
    private final EventJoin eventJoin;

    private HelpEvent helpCommand;

    public BotConfig(List<Event> events, Environment env, EventJoin eventJoin, HelpEvent helpCommand) {
        this.events = events;
        this.env = env;
        this.token = env.getProperty("discordBot.token");
        this.eventJoin = eventJoin;
        this.helpCommand = helpCommand;
        log.info("BotConfig initialized, token: {}, events size: {}",
                token != null && !token.isBlank() ? "present" : "missing", events.size());
    }

    /**
     * Создает экземпляр BotEvents и регистрирует команды.
     *
     * @return экземпляр BotEvents
     */
    @Bean
    public BotEvents createCommand() {
        log.info("Creating BotEvents with prefix: !");
        BotEvents botEvents = new BotEvents("!");
        events.sort((event1, event2) -> event1.getName().compareToIgnoreCase(event2.getName()));
        log.debug("Sorted events for registration, size: {}", events.size());
        botEvents.registerCommand(events);
        helpCommand.events = botEvents.getCommands();
        log.info("HelpCommand updated with {} commands", botEvents.getCommands().size());
        return botEvents;
    }

    /**
     * Создает экземпляр JDA и настраивает бота.
     *
     * @param botEvents экземпляр BotEvents для обработки событий
     * @return экземпляр JDA
     * @throws RuntimeException если токен недействителен
     */
    @Bean
    public JDA createBot(BotEvents botEvents) {
        log.info("Creating JDA instance");
        JDA jda;
        try {
            Files.deleteIfExists(READY_FILE);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("DISCORD_BOT_TOKEN is not configured");
            }

            jda = JDABuilder.create(token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("золотые чаши"))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(eventJoin)
                    .addEventListeners(botEvents)
                    .build()
                    .awaitReady();

            Files.writeString(READY_FILE, jda.getSelfUser().getId());
            log.info("JDA instance is ready, status: {}", jda.getStatus());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discord bot startup was interrupted", e);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to start JDA instance: {}", e.getMessage(), e);
            throw new IllegalStateException("Discord bot failed to start", e);
        }
        return jda;
    }

}
