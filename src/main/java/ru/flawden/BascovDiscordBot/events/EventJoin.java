package ru.flawden.BascovDiscordBot.events;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateAfkTimeoutEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class EventJoin extends ListenerAdapter {

    @Override
    public void onGuildUpdateAfkTimeout(@NotNull GuildUpdateAfkTimeoutEvent event) {
        int sec = event.getNewAfkTimeout().getSeconds();
        String time = "";
        if ((sec/60/60) >= 1) {
            time+= sec/60/60 + " часов, ";
            sec -= (sec/60/60) * 3600;
        }
        if ((sec/ 60) >= 1) {
            time+= sec/60 + " минут, ";
            sec -= (sec/60) * 60;
        }
        time += sec + " секунд";
        event.getGuild().getDefaultChannel().sendMessage("Таймаут афк канала был изменен и теперь составляет: " + time + ".").queue();
    }


}
