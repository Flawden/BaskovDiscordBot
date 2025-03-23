package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;

@Slf4j
@Component
public class StopEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        log.info("Stop command executed in guild: {}", event.getTextChannel().getGuild().getId());
        var musicManager = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        AudioTrack currentTrack = musicManager.audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            log.warn("No track is playing to stop in guild: {}", event.getTextChannel().getGuild().getId());
            embed.setTitle("⏹️ Ошибка остановки");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String stoppedTrackTitle = currentTrack.getInfo().title;

        musicManager.audioPlayer.stopTrack();
        musicManager.scheduler.queue.clear();
        log.info("Stopped track: {} in guild: {}, queue cleared",
                stoppedTrackTitle, event.getTextChannel().getGuild().getId());

        if (event.getSelfVoiceState().inAudioChannel()) {
            event.getGuild().getAudioManager().closeAudioConnection();
            log.info("Disconnected from voice channel in guild: {}", event.getTextChannel().getGuild().getId());
        }

        embed.setTitle("⏹️ Воспроизведение остановлено");
        embed.setDescription("Остановлена песня: `" + stoppedTrackTitle + "`\n" +
                "Очередь очищена, бот отключён от голосового канала.");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String helpMessage() {
        return "Останавливает воспроизведение всех песен";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}