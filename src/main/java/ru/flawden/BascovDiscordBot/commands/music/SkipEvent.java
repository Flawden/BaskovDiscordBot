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
public class SkipEvent implements Event {

    private final PlayerManager playerManager;
    private final MusicControlPolicy controlPolicy;

    public SkipEvent(PlayerManager playerManager, MusicControlPolicy controlPolicy) {
        this.playerManager = playerManager;
        this.controlPolicy = controlPolicy;
    }

    @Override
    public void execute(EventArgs event) {
        if (!MusicCommandReply.allowOrReply(event, controlPolicy.canControlPlayback(event))) {
            return;
        }

        GuildMusicManager musicManager = playerManager.findMusicManager(event.getGuild()).orElse(null);
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);
        AudioTrack currentTrack = musicManager == null ? null : musicManager.getAudioPlayer().getPlayingTrack();

        if (currentTrack == null) {
            embed.setTitle("⏭️ Ошибка пропуска");
            embed.setDescription("Сейчас ничего не играет!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String skippedTrackTitle = currentTrack.getInfo().title;
        AudioTrack nextTrack = musicManager.getScheduler().nextTrack();
        log.info("Track skipped in guild {}: {}", event.getGuild().getId(), skippedTrackTitle);

        embed.setTitle("⏭️ Песня пропущена");
        if (nextTrack == null) {
            embed.setDescription("Песня `" + skippedTrackTitle + "` пропущена.\nОчередь пуста.");
        } else {
            embed.setDescription("Песня `" + skippedTrackTitle + "` пропущена.\nСейчас играет: `"
                    + nextTrack.getInfo().title + "`");
        }
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
