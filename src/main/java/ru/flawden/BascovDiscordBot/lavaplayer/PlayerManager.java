package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Менеджер плеера, отвечающий за создание и управление
 * {@link GuildMusicManager} для каждой гильдии.
 * Этот класс также предоставляет методы для загрузки и воспроизведения
 * аудиотреков с использованием {@link AudioPlayerManager}.
 *
 * @author Flawden
 * @version 1.0
 */
public class PlayerManager {

    private static PlayerManager INSTANCE;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    /**
     * Получает экземпляр {@link GuildMusicManager} для заданной гильдии.
     *
     * @param guild гильдия, для которой требуется менеджер музыки
     * @return экземпляр {@link GuildMusicManager} для указанной гильдии
     */
    public GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    /**
     * Загружает трек по указанному URL и добавляет его в очередь воспроизведения.
     *
     * @param textChannel текстовый канал, в который будет отправлено сообщение о результате загрузки
     * @param trackURL URL трека для загрузки
     */
    public void loadAndPlay(TextChannel textChannel, String trackURL) {
        final GuildMusicManager musicManager = this.getMusicManager(textChannel.getGuild());
        this.audioPlayerManager.loadItemOrdered(musicManager, trackURL, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                musicManager.scheduler.queue(audioTrack);

                textChannel.sendMessage("Добавлено в очередь: "
                        + audioTrack.getInfo().title
                        + "'** by **'"
                        + audioTrack.getInfo().author
                        + "'**'").queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                final List<AudioTrack> tracks = audioPlaylist.getTracks();

                if (!tracks.isEmpty()) {
                    musicManager.scheduler.queue(tracks.get(0));
                    textChannel.sendMessage("Добавлено в очередь: "
                            + tracks.get(0).getInfo().title
                            + "'** by **'"
                            + tracks.get(0).getInfo().author
                            + "'**'").queue();
                } else {
                    textChannel.sendMessage("Песня не была найдена. Убедитесь, что вы ввели название без ошибок.").queue();
                }
            }

            @Override
            public void noMatches() {
                textChannel.sendMessage("Песня не была найдена. Убедитесь, что вы ввели название без ошибок.").queue();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                textChannel.sendMessage("Неизвестная ошибка.").queue();
            }
        });
    }

    /**
     * Получает экземпляр {@link PlayerManager}.
     *
     * @return экземпляр {@link PlayerManager}
     */
    public static PlayerManager getINSTANCE() {

        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }

        return INSTANCE;
    }

}
