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
public class PlayEvent implements Event {
    @Override
    public void execute(EventArgs event) {
        log.info("Play command executed in guild: {}", event.getTextChannel().getGuild().getId());
        AudioPlayer audioPlayer = PlayerManager.getINSTANCE()
                .getMusicManager(event.getTextChannel().getGuild())
                .audioPlayer;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (audioPlayer.getPlayingTrack() == null) {
            log.warn("No track is playing to resume in guild: {}", event.getTextChannel().getGuild().getId());
            embed.setTitle("▶️ Ошибка воспроизведения");
            embed.setDescription("Сейчас ничего не играет! Используй `search <название или URL>`, чтобы запустить песню.");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }
        if (!audioPlayer.isPaused()) {
            log.info("Playback already active in guild: {}", event.getTextChannel().getGuild().getId());
            embed.setTitle("▶️ Уже играет");
            embed.setDescription("Воспроизведение уже идёт!\n" +
                    "Текущая песня: `" + audioPlayer.getPlayingTrack().getInfo().title + "`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }
        audioPlayer.setPaused(false);
        log.info("Playback resumed in guild: {}", event.getTextChannel().getGuild().getId());
        embed.setTitle("▶️ Воспроизведение возобновлено");
        embed.setDescription("Музыка успешно возобновлена.\n" +
                "Текущая песня: `" + audioPlayer.getPlayingTrack().getInfo().title + "`");
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();

    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String helpMessage() {
        return "Продолжает воспроизведение песни";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
