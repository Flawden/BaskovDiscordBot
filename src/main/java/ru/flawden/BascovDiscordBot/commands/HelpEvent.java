package ru.flawden.BascovDiscordBot.commands;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.util.List;

@Component
public class HelpEvent implements Event {

    public List<Event> events;

    @Override
    public void execute(EventArgs event) {
        String message = "Список комманд для Баскова:\n\n";
        for (Event command: events) {
            message+= command.getName() + " ====> " + command.helpMessage() + ".\n";
        }
        event.getTextChannel().sendMessage(message).queue();
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String helpMessage() {
        return "Выводит список всех доступных команд.";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
