package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.Color;

@Slf4j
@Component
public class StopEvent implements Event {

    private final PlayerManager playerManager;
    private final MusicControlPolicy controlPolicy;

    public StopEvent(PlayerManager playerManager, MusicControlPolicy controlPolicy) {
        this.playerManager = playerManager;
        this.controlPolicy = controlPolicy;
    }

    @Override
    public void execute(EventArgs event) {
        if (!MusicCommandReply.allowOrReply(event, controlPolicy.canControlPlayback(event))) {
            return;
        }

        GuildMusicManager musicManager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        AudioTrack currentTrack = musicManager == null ? null : musicManager.getAudioPlayer().getPlayingTrack();
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);

        if (currentTrack == null) {
            embed.setTitle("⏹️ Ошибка остановки");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String stoppedTrackTitle = currentTrack.getInfo().title;
        playerManager.recordExplicitStopFeedback(event.getGuild());
        playerManager.stopAndRelease(event.getGuild());
        log.info("Music session stopped and released in guild {}", event.getGuild().getId());

        embed.setTitle("⏹️ Воспроизведение остановлено");
        embed.setDescription("Остановлена песня: `" + stoppedTrackTitle + "`\n"
                + "Очередь очищена, бот отключён от голосового канала.");
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
        return "Останавливает музыку, очищает очередь и отключает бота";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
