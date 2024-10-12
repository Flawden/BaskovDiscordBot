package ru.flawden.BascovDiscordBot.config.eventconfig;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Класс BotEvents обрабатывает события сообщений в Discord и управляет командами бота.
 * Он позволяет регистрировать команды и проверять, были ли введены команды пользователями.
 * В зависимости от прав пользователя, команды могут выполняться или нет.
 *
 * @author Flawden
 * @version 1.0
 */
public class BotEvents extends ListenerAdapter {

    private final String prefix;
    private MessageReceivedEvent event;

    private List<Event> events;

    public BotEvents(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Возвращает список зарегистрированных команд.
     *
     * @return список команд
     */
    public List<Event> getCommands() {
        return events;
    }

    /**
     * Регистрирует новую команду.
     *
     * @param event команда для регистрации
     */
    public void registerCommand(Event event) {
        this.events.add(event);
    }

    /**
     * Регистрирует несколько команд.
     *
     * @param events список команд для регистрации
     */
    public void registerCommand(List<Event> events) {
        this.events = events;
    }

    /**
     * Обрабатывает входящие сообщения.
     *
     * @param event событие полученного сообщения
     */
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getMessage().getContentRaw().startsWith(this.prefix)) {
            this.event = event;
            init();
        }
    }

    /**
     * Инициализирует выполнение команды, если она была введена.
     */
    private void init() {
        for (int command = 0; command <= events.size() - 1; command++) {
            if (isEnterCommandEqualToExisting(events.get(command))) {
                executeIfCommandAvailable(events.get(command));
                break;
            } else if (command >= events.size() - 1) {
                event.getChannel().asTextChannel().sendMessage("Я не знаю данную команду.").queue();
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
            execute(event);
        } else {
            this.event.getChannel().sendMessage("Данная команда доступна только создателю сервера.").queue();
        }
    }

    /**
     * Проверяет, есть ли у пользователя права на выполнение команды.
     *
     * @param event команда для проверки
     * @return true, если у пользователя есть права; иначе false
     */
    private boolean checkPermissionToExecute(Event event) {
        return !event.needOwner() || this.event.getMember().isOwner();
    }

    /**
     * Проверяет, совпадает ли введенная команда с зарегистрированной командой.
     *
     * @param event команда для проверки
     * @return true, если команда совпадает; иначе false
     */
    private boolean isEnterCommandEqualToExisting(Event event) {
        return this.event.getMessage().getContentRaw().split(" ")[0].equalsIgnoreCase(this.prefix + event.getName());
    }

    /**
     * Выполняет указанную команду.
     *
     * @param event команда для выполнения
     */
    private void execute(Event event) {
        event.execute(new EventArgs(this.event));
    }

}