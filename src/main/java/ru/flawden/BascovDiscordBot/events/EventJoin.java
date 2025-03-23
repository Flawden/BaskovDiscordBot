package ru.flawden.BascovDiscordBot.events;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateAfkTimeoutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Обработчик событий, связанных с обновлениями сервера Discord.
 * Этот класс слушает изменения в настройках сервера и выполняет действия
 * при обновлении таймаута для канала AFK (Away From Keyboard).
 *
 * @author Flawden
 * @version 1.0
 */
@Slf4j
@Component
public class EventJoin extends ListenerAdapter {

    /**
     * Вызывается, когда изменяется таймаут для канала AFK.
     *
     * @param event событие, содержащее информацию об обновлении таймаута
     */
    @Override
    public void onGuildUpdateAfkTimeout(@NotNull GuildUpdateAfkTimeoutEvent event) {
        int sec = event.getNewAfkTimeout().getSeconds();
        String time = "";
        if ((sec / 60 / 60) >= 1) {
            time += sec / 60 / 60 + " часов, ";
            sec -= (sec / 60 / 60) * 3600;
        }
        if ((sec / 60) >= 1) {
            time += sec / 60 + " минут, ";
            sec -= (sec / 60) * 60;
        }
        time += sec + " секунд";

        log.info("AFK timeout updated in guild: {}, new timeout: {}",
                event.getGuild().getId(), time);
        event.getGuild().getDefaultChannel().asTextChannel()
                .sendMessage("Таймаут афк канала был изменен и теперь составляет: " + time + ".")
                .queue();
    }


}
