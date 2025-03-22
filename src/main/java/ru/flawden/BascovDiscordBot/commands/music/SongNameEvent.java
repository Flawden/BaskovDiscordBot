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
public class SongNameEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        AudioPlayer audioPlayer = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild())
                .scheduler.audioPlayer;
        AudioTrack currentTrack = audioPlayer.getPlayingTrack();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (currentTrack == null) {
            embed.setTitle("🎵 Ошибка");
            embed.setDescription("В данный момент нет воспроизводимых песен!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String trackTitle = currentTrack.getInfo().title;
        String trackAuthor = currentTrack.getInfo().author;
        long position = currentTrack.getPosition();
        long duration = currentTrack.getDuration();
        boolean isPaused = audioPlayer.isPaused();

        if (trackTitle.length() > 50) {
            trackTitle = trackTitle.substring(0, 47) + "...";
        }
        if (trackAuthor.length() > 50) {
            trackAuthor = trackAuthor.substring(0, 47) + "...";
        }

        embed.setTitle("🎵 Сейчас играет");
        embed.setDescription("**Название:** `" + trackTitle + "`\n" +
                "**Автор:** `" + trackAuthor + "`\n" +
                "**Позиция:** `" + formatTime(position) + " / " + formatTime(duration) + "`\n" +
                (isPaused ? "⚠️ Воспроизведение на паузе" : ""));
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
        return "SongName";
    }

    @Override
    public String helpMessage() {
        return "Отображает название проигрываемой композиции";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}