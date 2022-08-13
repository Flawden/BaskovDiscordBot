package ru.flawden.BascovDiscordBot.config;

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
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.events.EventJoin;
import ru.flawden.BascovDiscordBot.config.eventconfig.BotEvents;

import javax.security.auth.login.LoginException;
import java.util.List;

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
    }

    @Bean
    public BotEvents createCommand() {
        BotEvents botEvents = new BotEvents("!");
        botEvents.registerCommand(events);
        helpCommand.events = botEvents.getCommands();
        return botEvents;
    }

    @Bean
    public JDA createBot(BotEvents botEvents) {
        JDA jda;
        try {
            jda = JDABuilder.create(token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("золотые чаши"))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(eventJoin)
                    .addEventListeners(botEvents)
                    .build();
        } catch (LoginException e) {
            throw new RuntimeException("Упс! Вы использовали неверный токен. Попробуйте другой!");
        }
        return jda;
    }

}
