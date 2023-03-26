package ru.flawden.BascovDiscordBot.config.eventconfig;

import java.util.List;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class BotEvents extends ListenerAdapter {

    private final String prefix;
    private MessageReceivedEvent event;

    private List<Event> events;

    public BotEvents(String prefix) {
        this.prefix = prefix;
    }

    public List<Event> getCommands() {
        return events;
    }

    public void registerCommand(Event event) {
        this.events.add(event);
    }

    public void registerCommand(List<Event> events) {
        this.events = events;
    }

    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getMessage().getContentRaw().startsWith(this.prefix)) {
            this.event = event;
            init();
        }
    }

    private void init() {
        for(int command = 0; command <= events.size() - 1; command++) {
            if(isEnterCommandEqualToExisting(events.get(command))) {
                executeIfCommandAvailable(events.get(command));
                break;
            }
            else if(command >= events.size() - 1) {
                event.getChannel().asTextChannel().sendMessage("Я не знаю данную команду.").queue();
            }
        }
    }

    private void executeIfCommandAvailable(Event event) {
        if(checkPermissionToExecute(event)) {
            execute(event);
        } else {
            this.event.getChannel().sendMessage("Данная команда доступна только создателю сервера.").queue();
        }
    }

    private boolean checkPermissionToExecute(Event event) {
        return !event.needOwner() || this.event.getMember().isOwner();
    }

    private boolean isEnterCommandEqualToExisting(Event event) {
        return this.event.getMessage().getContentRaw().split(" ")[0].equalsIgnoreCase(this.prefix + event.getName());
    }

    private void execute(Event event) {
        event.execute(new EventArgs(this.event));
    }

}