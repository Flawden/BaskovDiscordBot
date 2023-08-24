package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.springframework.core.io.support.ResourcePatternUtils.isUrl;

@Component
public class SetPlayingTimeEvent implements Event {

    private EventArgs event;

    @Override
    public void execute(EventArgs event) {
        this.event = event;
        if(!isAnyArgs()) {
            return;
        };
        String link = event.getArgs()[1];
        String timePattern = "^([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$";
        if(!link.matches(timePattern)) {
            event.getTextChannel().sendMessage("Ошибка! Для работы команды введите время в формате: ЧЧ:ММ:СС").queue();
            return;
        }
        String[] parts = link.split(":");
        long time = ((Integer.parseInt(parts[0]) * 3600L) + (Integer.parseInt(parts[1]) * 60L) + (Integer.parseInt(parts[2]))) * 1000L;
        PlayerManager.getINSTANCE().getMusicManager(event.getTextChannel().getGuild()).scheduler.audioPlayer.getPlayingTrack().setPosition(time);
        event.getTextChannel().sendMessage("Воспроизведение будет начато с указанного времени после загрузки.").queue();
    }

    @Override
    public String getName() {
        return "SetPlayingTime";
    }

    @Override
    public String helpMessage() {
        return "Воспроизводит текущую песню с указанного момента. Для работы команды введите следующую команду: \"!SetPlayingTime ЧЧ:ММ:СС\"";
    }

    @Override
    public boolean needOwner() {
        return false;
    }

    private boolean isAnyArgs() {
        boolean isAnyARGS = true;
        if (event.getArgs().length <= 1) {
            isAnyARGS = false;
            event.getTextChannel().sendMessage("Введите желаемое время воспроизведение для перехода - \" !SetPlayingTime 00:00:00\"").queue();
        }
        return isAnyARGS;
    }
}
