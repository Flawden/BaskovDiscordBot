package ru.flawden.BascovDiscordBot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.Color;

@Component
public class SongNameEvent implements Event {

    private final PlayerManager playerManager;

    public SongNameEvent(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void execute(EventArgs event) {
        AudioPlayer audioPlayer = playerManager.findMusicManager(event.getGuild())
                .map(manager -> manager.getAudioPlayer())
                .orElse(null);
        AudioTrack currentTrack = audioPlayer == null ? null : audioPlayer.getPlayingTrack();
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.CYAN);

        if (currentTrack == null) {
            embed.setTitle("🎵 Ошибка");
            embed.setDescription("В данный момент нет воспроизводимых песен!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        embed.setTitle("🎵 Сейчас играет");
        embed.setDescription("**Название:** `" + shorten(currentTrack.getInfo().title) + "`\n"
                + "**Автор:** `" + shorten(currentTrack.getInfo().author) + "`\n"
                + "**Позиция:** `" + formatTime(currentTrack.getPosition()) + " / "
                + formatTime(currentTrack.getDuration()) + "`\n"
                + (audioPlayer.isPaused() ? "⚠️ Воспроизведение на паузе" : ""));
        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private static String shorten(String value) {
        return value.length() > 50 ? value.substring(0, 47) + "..." : value;
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "SongName";
    }

    @Override
    public String helpMessage() {
        return "Отображает название проигрываемой композиции";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
