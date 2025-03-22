package ru.flawden.BascovDiscordBot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.awt.*;
import java.util.concurrent.TimeUnit;

@Component
public class SpamEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (event.getArgs().length <= 1) {
            embed.setTitle("📩 Ошибка спама");
            embed.setDescription("Укажи пользователя для спама!\n" +
                    "Пример: `!spam @Nick`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String mention = event.getArgs()[1];
        User targetUser = null;
        if (mention.matches("<@!?\\d+>")) {
            String userId = mention.replaceAll("[^\\d]", "");
            targetUser = event.getGuild().getMemberById(userId) != null ?
                    event.getGuild().getMemberById(userId).getUser() : null;
        }

        if (targetUser == null) {
            embed.setTitle("📩 Ошибка спама");
            embed.setDescription("Не удалось найти пользователя по упоминанию: `" + mention + "`\n" +
                    "Укажи валидное упоминание, например: `!spam @Nick`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        embed.setTitle("📩 Спам начат");
        embed.setDescription("Пользователь " + event.getMember().getAsMention() + " начал спам для " +
                targetUser.getAsMention() + "!\n" +
                "Будет отправлено 10 сообщений.");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();

        for (int i = 0; i < 10; i++) {
            embed = new EmbedBuilder();
            embed.setColor(Color.CYAN);
            embed.setFooter("Сообщение " + (i + 1) + " из 10", null);
            embed.setDescription(targetUser.getAsMention() + " !!! Просыпайся!!!");
            event.getTextChannel().sendMessageEmbeds(embed.build())
                    .queueAfter(i * 500, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public String getGroup() {
        return "Общие";
    }

    @Override
    public String getName() {
        return "spam";
    }

    @Override
    public String helpMessage() {
        return "Команда призвана последовательностью из 10 сообщений с упоминанием позвать участника сервера. Пример: spam @Nick";
    }

    @Override
    public boolean needOwner() {
        return true;
    }
}