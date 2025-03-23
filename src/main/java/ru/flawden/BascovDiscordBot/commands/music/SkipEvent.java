package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;

@Slf4j
@Component
public class SkipEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        log.info("Skip command executed in guild: {}", event.getTextChannel().getGuild().getId());
        GuildMusicManager musicManager = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        AudioTrack currentTrack = musicManager.audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            log.warn("No track is playing to skip in guild: {}", event.getTextChannel().getGuild().getId());
            embed.setTitle("⏭️ Ошибка пропуска");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String skippedTrackTitle = currentTrack.getInfo().title;
        log.info("Skipping track: {} in guild: {}", skippedTrackTitle, event.getTextChannel().getGuild().getId());

        musicManager.scheduler.nextTrack();

        AudioTrack nextTrack = musicManager.audioPlayer.getPlayingTrack();
        if (nextTrack == null) {
            log.info("Skipped last track: {} in guild: {}, queue is empty",
                    skippedTrackTitle, event.getTextChannel().getGuild().getId());
            embed.setTitle("⏭️ Песня пропущена");
            embed.setDescription("Песня `" + skippedTrackTitle + "` пропущена.\n" +
                    "Очередь пуста — это была последняя песня!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        log.info("Skipped track: {} in guild: {}, now playing: {}",
                skippedTrackTitle, event.getTextChannel().getGuild().getId(), nextTrack.getInfo().title);
        embed.setTitle("⏭️ Песня пропущена");
        embed.setDescription("Песня `" + skippedTrackTitle + "` пропущена.\n" +
                "Сейчас играет: `" + nextTrack.getInfo().title + "`");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "skip";
    }

    @Override
    public String helpMessage() {
        return "Пропускает воспроизведение песни";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}