package ru.flawden.BascovDiscordBot.commands;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

@Component
public class SpamEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        String name = event.getArgs()[1];
        for (int i = 0; i < 10; i++) {
            event.getTextChannel().sendMessage(name + " !!! Просыпайся!!!").queue();
        }
    }

    @Override
    public String getName() {
        return "spam";
    }

    @Override
    public String helpMessage() {
        return "Команда призвана последовательностью из 10 сообщений с упоминанием позвать участника сервера. Пример: spam @Nick.";
    }

    @Override
    public boolean needOwner() {
        return true;
    }
}
