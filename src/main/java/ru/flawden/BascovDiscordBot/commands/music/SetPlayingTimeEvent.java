package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.Color;

@Slf4j
@Component
public class SetPlayingTimeEvent implements Event {

    private final PlayerManager playerManager;
    private final MusicControlPolicy controlPolicy;

    public SetPlayingTimeEvent(PlayerManager playerManager, MusicControlPolicy controlPolicy) {
        this.playerManager = playerManager;
        this.controlPolicy = controlPolicy;
    }

    @Override
    public void execute(EventArgs event) {
        if (!MusicCommandReply.allowOrReply(event, controlPolicy.canControlPlayback(event))) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);
        if (event.getArguments().isEmpty()) {
            embed.setTitle("⏳ Ошибка установки времени");
            embed.setDescription("Введите время в формате: `!SetPlayingTime ЧЧ:ММ:СС`\n"
                    + "Пример: `!SetPlayingTime 00:01:30`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        AudioPlayer audioPlayer = playerManager.findMusicManager(event.getGuild())
                .map(manager -> manager.getAudioPlayer())
                .orElse(null);
        AudioTrack currentTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            embed.setTitle("⏳ Ошибка установки времени");
            embed.setDescription("В данный момент нет воспроизводимых песен!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String timeInput = event.getArguments().get(0);
        String timePattern = "^([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$";
        if (!timeInput.matches(timePattern)) {
            embed.setTitle("⏳ Ошибка формата времени");
            embed.setDescription("Введите время в формате: `ЧЧ:ММ:СС`\n"
                    + "Пример: `!SetPlayingTime 00:01:30`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String[] parts = timeInput.split(":");
        long timeInMillis = ((Integer.parseInt(parts[0]) * 3600L)
                + (Integer.parseInt(parts[1]) * 60L)
                + Integer.parseInt(parts[2])) * 1000L;

        if (timeInMillis > currentTrack.getDuration()) {
            embed.setTitle("⏳ Ошибка установки времени");
            embed.setDescription("Указанное время (`" + timeInput + "`) превышает длительность трека!\n"
                    + "Длительность трека: `" + formatTime(currentTrack.getDuration()) + "`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        currentTrack.setPosition(timeInMillis);
        log.info("Track seeked in guild {} to {}", event.getGuild().getId(), timeInput);
        embed.setTitle("⏳ Время установлено");
        embed.setDescription("Трек `" + currentTrack.getInfo().title + "` перемотан на `" + timeInput + "`.");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
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
        return "Перематывает текущую песню: !SetPlayingTime ЧЧ:ММ:СС";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
