package ru.flawden.BascovDiscordBot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HelpEvent implements Event {

    private volatile List<Event> events = List.of();

    public void setEvents(List<Event> events) {
        this.events = List.copyOf(events);
    }

    @Override
    public void execute(EventArgs event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📜 Список команд для Баскова");
        embed.setDescription("Slash-команды — основной интерфейс. Старые prefix-команды не чувствительны к регистру и пока поддерживаются.");
        embed.setColor(Color.CYAN);

        Map<String, List<Event>> groupedCommands = events.stream()
                .collect(Collectors.groupingBy(
                        Event::getGroup,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<Event>> entry : groupedCommands.entrySet()) {
            StringBuilder groupField = new StringBuilder();
            for (Event command : entry.getValue()) {
                groupField.append('`')
                        .append(command.getName())
                        .append("` — ")
                        .append(command.helpMessage())
                        .append('\n');
            }
            embed.addField("🎯 " + entry.getKey(), groupField.toString(), false);
        }

        log.info("Help displayed in guild {} with {} command groups",
                event.getGuild().getId(), groupedCommands.size());
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
