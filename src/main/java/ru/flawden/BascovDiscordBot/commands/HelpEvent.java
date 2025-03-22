package ru.flawden.BascovDiscordBot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HelpEvent implements Event {

    public List<Event> events;

    @Override
    public void execute(EventArgs event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📜 Список команд для Баскова");
        embed.setDescription("Команды не чувствительны к регистру");
        embed.setColor(Color.CYAN);

        Map<String, List<Event>> groupedCommands = events.stream()
                .collect(Collectors.groupingBy(Event::getGroup));

        for (Map.Entry<String, List<Event>> entry : groupedCommands.entrySet()) {
            String group = entry.getKey();
            StringBuilder groupField = new StringBuilder();

            for (Event command : entry.getValue()) {
                groupField.append("`")
                        .append(command.getName())
                        .append("` — ")
                        .append(command.helpMessage())
                        .append("\n");
            }
            embed.addField("🎯 " + group, groupField.toString(), false);
        }
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public String getGroup() {
        return "Общие";
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String helpMessage() {
        return "Выводит список всех доступных команд";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
