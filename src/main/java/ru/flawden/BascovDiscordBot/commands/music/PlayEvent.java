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
public class PlayEvent implements Event {

    private final PlayerManager playerManager;
    private final MusicControlPolicy controlPolicy;

    public PlayEvent(PlayerManager playerManager, MusicControlPolicy controlPolicy) {
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
            embed.setTitle("▶️ Ошибка воспроизведения");
            embed.setDescription("Сейчас ничего не играет! Используй `search <название или URL>`.");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (!audioPlayer.isPaused()) {
            embed.setTitle("▶️ Уже играет");
            embed.setDescription("Текущая песня: `" + audioPlayer.getPlayingTrack().getInfo().title + "`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        audioPlayer.setPaused(false);
        log.info("Playback resumed in guild {}", event.getGuild().getId());
        embed.setTitle("▶️ Воспроизведение возобновлено");
        embed.setDescription("Текущая песня: `" + audioPlayer.getPlayingTrack().getInfo().title + "`");
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
