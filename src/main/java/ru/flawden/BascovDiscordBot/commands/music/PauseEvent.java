package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;

@Slf4j
@Component
public class PauseEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        log.info("Pause command executed in guild: {}", event.getTextChannel().getGuild().getId());
        AudioPlayer audioPlayer = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild())
                .audioPlayer;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (audioPlayer.getPlayingTrack() == null) {
            log.warn("No track is playing to pause in guild: {}", event.getTextChannel().getGuild().getId());
            embed.setTitle("⏸️ Ошибка паузы");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (audioPlayer.isPaused()) {
            log.info("Playback already paused in guild: {}", event.getTextChannel().getGuild().getId());
            embed.setTitle("⏸️ Пауза уже включена");
            embed.setDescription("Воспроизведение уже приостановлено. Используй `play`, чтобы продолжить!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        audioPlayer.setPaused(true);
        log.info("Playback paused in guild: {}, track: {}", event.getTextChannel().getGuild().getId(), audioPlayer.getPlayingTrack().getInfo().title);

        embed.setTitle("⏸️ Воспроизведение приостановлено");
        embed.setDescription("Музыка успешно поставлена на паузу.\n" +
                "Текущая песня: `" + audioPlayer.getPlayingTrack().getInfo().title + "`");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public String helpMessage() {
        return "Приостанавливает воспроизведение песни";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
