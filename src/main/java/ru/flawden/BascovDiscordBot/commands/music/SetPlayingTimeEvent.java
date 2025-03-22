package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;

@Component
public class SetPlayingTimeEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (event.getArgs().length <= 1) {
            embed.setTitle("⏳ Ошибка установки времени");
            embed.setDescription("Введите желаемое время в формате: `!SetPlayingTime ЧЧ:ММ:СС`\n" +
                    "Пример: `!SetPlayingTime 00:01:30`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        AudioPlayer audioPlayer = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild())
                .scheduler.audioPlayer;
        AudioTrack currentTrack = audioPlayer.getPlayingTrack();

        if (currentTrack == null) {
            embed.setTitle("⏳ Ошибка установки времени");
            embed.setDescription("В данный момент нет воспроизводимых песен!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String timeInput = event.getArgs()[1];
        String timePattern = "^([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$";
        if (!timeInput.matches(timePattern)) {
            embed.setTitle("⏳ Ошибка формата времени");
            embed.setDescription("Введите время в формате: `ЧЧ:ММ:СС`\n" +
                    "Пример: `!SetPlayingTime 00:01:30`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String[] parts = timeInput.split(":");
        long timeInSeconds = (Integer.parseInt(parts[0]) * 3600L) +
                (Integer.parseInt(parts[1]) * 60L) +
                Integer.parseInt(parts[2]);
        long timeInMillis = timeInSeconds * 1000L;

        long trackDuration = currentTrack.getDuration();
        if (timeInMillis > trackDuration) {
            embed.setTitle("⏳ Ошибка установки времени");
            embed.setDescription("Указанное время (`" + timeInput + "`) превышает длительность трека!\n" +
                    "Длительность трека: `" + formatTime(trackDuration) + "`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        currentTrack.setPosition(timeInMillis);

        embed.setTitle("⏳ Время установлено");
        embed.setDescription("Воспроизведение трека `" + currentTrack.getInfo().title + "` " +
                "перемотано на `" + timeInput + "`.");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    @Override
    public String getGroup() {
        return "Музыка";
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
}