package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;

@Component
public class SkipEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        GuildMusicManager musicManager = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        AudioTrack currentTrack = musicManager.audioPlayer.getPlayingTrack();
        if (currentTrack == null) {
            embed.setTitle("⏭️ Ошибка пропуска");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String skippedTrackTitle = currentTrack.getInfo().title;

        musicManager.scheduler.nextTrack();

        AudioTrack nextTrack = musicManager.audioPlayer.getPlayingTrack();
        if (nextTrack == null) {
            embed.setTitle("⏭️ Песня пропущена");
            embed.setDescription("Песня `" + skippedTrackTitle + "` пропущена.\n" +
                    "Очередь пуста — это была последняя песня!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

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