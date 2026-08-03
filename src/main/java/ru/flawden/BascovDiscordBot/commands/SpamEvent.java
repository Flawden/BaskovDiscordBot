package ru.flawden.BascovDiscordBot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SpamEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        log.info("Spam command executed in guild: {}, args: {}",
                event.getTextChannel().getGuild().getId(), String.join(" ", event.getArgs()));
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (event.getArgs().length <= 1) {
            log.warn("No user provided for spam in guild: {}", event.getTextChannel().getGuild().getId());
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
            log.warn("Invalid user mention for spam in guild: {}, mention: {}",
                    event.getTextChannel().getGuild().getId(), mention);
            embed.setTitle("📩 Ошибка спама");
            embed.setDescription("Не удалось найти пользователя по упоминанию: `" + mention + "`\n" +
                    "Укажи валидное упоминание, например: `!spam @Nick`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        log.info("Starting spam for user: {} in guild: {}", targetUser.getName(), event.getTextChannel().getGuild().getId());
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
            log.debug("Sent spam message {} of 10 for user: {} in guild: {}",
                    i + 1, targetUser.getName(), event.getTextChannel().getGuild().getId());
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

    @Override
    public Duration cooldown() {
        return Duration.ofSeconds(30);
    }
}
