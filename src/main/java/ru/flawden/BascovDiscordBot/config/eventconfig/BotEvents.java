package ru.flawden.BascovDiscordBot.config.eventconfig;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Класс BotEvents обрабатывает события сообщений в Discord и управляет командами бота.
 * Он позволяет регистрировать команды и проверять, были ли введены команды пользователями.
 * В зависимости от прав пользователя, команды могут выполняться или нет.
 *
 * @author Flawden
 * @version 1.0
 */
@Slf4j
public class BotEvents extends ListenerAdapter {

    private final String prefix;
    private MessageReceivedEvent event;

    private List<Event> events;

    public BotEvents(String prefix) {
        this.prefix = prefix;
        log.info("BotEvents initialized with prefix: {}", prefix);
    }

    /**
     * Возвращает список зарегистрированных команд.
     *
     * @return список команд
     */
    public List<Event> getCommands() {
        log.debug("Returning list of registered commands, size: {}", events != null ? events.size() : 0);
        return events;
    }

    /**
     * Регистрирует новую команду.
     *
     * @param event команда для регистрации
     */
    public void registerCommand(Event event) {
        this.events.add(event);
        log.info("Registered new command: {}", event.getName());
    }

    /**
     * Регистрирует несколько команд.
     *
     * @param events список команд для регистрации
     */
    public void registerCommand(List<Event> events) {
        this.events = events;
        log.info("Registered {} commands", events.size());
    }

    /**
     * Обрабатывает входящие сообщения.
     *
     * @param event событие полученного сообщения
     */
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw();
        if (content.startsWith(this.prefix)) {
            log.debug("Received message with prefix in guild: {}, content: {}",
                    event.getGuild().getId(), content);
            this.event = event;
            init();
        } else {
            log.trace("Message ignored, does not start with prefix: {}, content: {}",
                    this.prefix, content);
        }
    }

    /**
     * Инициализирует выполнение команды, если она была введена.
     */
    private void init() {
        String commandInput = this.event.getMessage().getContentRaw().split(" ")[0];
        log.info("Processing command: {} in guild: {}", commandInput, event.getGuild().getId());

        for (int command = 0; command <= events.size() - 1; command++) {
            Event currentEvent = events.get(command);
            if (isEnterCommandEqualToExisting(currentEvent)) {
                log.info("Found matching command: {} in guild: {}", currentEvent.getName(), event.getGuild().getId());
                executeIfCommandAvailable(currentEvent);
                break;
            } else if (command >= events.size() - 1) {
                log.warn("Unknown command: {} in guild: {}", commandInput, event.getGuild().getId());
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("❌ Ошибка");
                embed.setDescription("Я не знаю данную команду.");
                embed.setColor(Color.RED);
                event.getChannel().asTextChannel().sendMessageEmbeds(embed.build()).queue();
            }
        }
    }

    /**
     * Выполняет команду, если у пользователя есть права.
     *
     * @param event команда для выполнения
     */
    private void executeIfCommandAvailable(Event event) {
        if (checkPermissionToExecute(event)) {
            log.info("Executing command: {} in guild: {} by user: {}",
                    event.getName(), this.event.getGuild().getId(), this.event.getMember().getEffectiveName());
            execute(event);
        } else {
            log.warn("User {} does not have permission to execute command: {} in guild: {}",
                    this.event.getMember().getEffectiveName(), event.getName(), this.event.getGuild().getId());
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("⚠️ Доступ ограничен");
            embed.setDescription("Данная команда доступна только создателю сервера.");
            embed.setColor(Color.YELLOW);
            this.event.getChannel().sendMessageEmbeds(embed.build()).queue();
        }
    }

    /**
     * Проверяет, есть ли у пользователя права на выполнение команды.
     *
     * @param event команда для проверки
     * @return true, если у пользователя есть права; иначе false
     */
    private boolean checkPermissionToExecute(Event event) {
        boolean hasPermission = !event.needOwner() || this.event.getMember().isOwner();
        log.debug("Permission check for command: {} in guild: {}, result: {}",
                event.getName(), this.event.getGuild().getId(), hasPermission);
        return hasPermission;
    }

    /**
     * Проверяет, совпадает ли введенная команда с зарегистрированной командой.
     *
     * @param event команда для проверки
     * @return true, если команда совпадает; иначе false
     */
    private boolean isEnterCommandEqualToExisting(Event event) {
        boolean isMatch = this.event.getMessage().getContentRaw().split(" ")[0]
                .equalsIgnoreCase(this.prefix + event.getName());
        log.trace("Checking command match: {} against input: {}, result: {}",
                event.getName(), this.event.getMessage().getContentRaw().split(" ")[0], isMatch);
        return isMatch;
    }

    /**
     * Выполняет указанную команду.
     *
     * @param event команда для выполнения
     */
    private void execute(Event event) {
        log.debug("Executing command: {} with args: {}",
                event.getName(), String.join(" ", this.event.getMessage().getContentRaw().split(" ")));
        event.execute(new EventArgs(this.event));
    }

}