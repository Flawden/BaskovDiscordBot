package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.Color;

@Slf4j
@Component
public class PauseEvent implements Event {

    private final PlayerManager playerManager;
    private final MusicControlPolicy controlPolicy;

    public PauseEvent(PlayerManager playerManager, MusicControlPolicy controlPolicy) {
        this.playerManager = playerManager;
        this.controlPolicy = controlPolicy;
    }

    @Override
    public void execute(EventArgs event) {
        if (!MusicCommandReply.allowOrReply(event, controlPolicy.canControlPlayback(event))) {
            return;
        }

        AudioPlayer audioPlayer = playerManager.findMusicManager(event.getGuild())
                .map(manager -> manager.getAudioPlayer())
                .orElse(null);
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);

        if (audioPlayer == null || audioPlayer.getPlayingTrack() == null) {
            embed.setTitle("⏸️ Ошибка паузы");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (audioPlayer.isPaused()) {
            embed.setTitle("⏸️ Пауза уже включена");
            embed.setDescription("Воспроизведение уже приостановлено. Используй `play`, чтобы продолжить!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        audioPlayer.setPaused(true);
        log.info("Playback paused in guild {}", event.getGuild().getId());
        embed.setTitle("⏸️ Воспроизведение приостановлено");
        embed.setDescription("Текущая песня: `" + audioPlayer.getPlayingTrack().getInfo().title + "`");
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
