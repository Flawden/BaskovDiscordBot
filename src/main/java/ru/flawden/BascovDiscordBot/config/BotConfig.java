package ru.flawden.BascovDiscordBot.config;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import ru.flawden.BascovDiscordBot.commands.HelpEvent;
import ru.flawden.BascovDiscordBot.config.eventconfig.BotEvents;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.events.EventJoin;

import java.util.Collections;
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
@PropertySource("classpath:application.properties")
public class BotConfig {

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
                token != null ? "present" : "missing", events.size());
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
            jda = JDABuilder.create(token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("золотые чаши"))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(eventJoin)
                    .addEventListeners(botEvents)
                    .build();
            log.info("JDA instance created successfully, status: {}", jda.getStatus());
        } catch (Exception e) {
            log.error("Failed to create JDA instance: {}", e.getMessage(), e);
            throw new RuntimeException("Упс! Вы использовали неверный токен. Попробуйте другой!");
        }
        return jda;
    }

}
