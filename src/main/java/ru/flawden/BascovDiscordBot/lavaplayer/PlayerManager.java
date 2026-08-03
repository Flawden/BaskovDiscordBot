package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
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
@Slf4j
public class PlayerManager {

    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    public PlayerManager() {
        this.musicManagers = new ConcurrentHashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        log.info("PlayerManager initialized");
    }

    /**
     * Получает экземпляр {@link GuildMusicManager} для заданной гильдии.
     *
     * @param guild гильдия, для которой требуется менеджер музыки
     * @return экземпляр {@link GuildMusicManager} для указанной гильдии
     */
    public GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager, guild);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            log.debug("Created new GuildMusicManager for guild: {}", guild.getId());
            return guildMusicManager;
        });
    }

    /**
     * Загружает трек по-указанному URL и добавляет его в очередь воспроизведения.
     *
     * @param textChannel текстовый канал, в который будет отправлено сообщение о результате загрузки
     * @param trackURL    URL трека для загрузки
     */
    public void loadAndPlay(TextChannel textChannel, String trackURL) {
        final GuildMusicManager musicManager = this.getMusicManager(textChannel.getGuild());
        log.info("Loading track: {} for guild: {}", trackURL, textChannel.getGuild().getId());
        this.audioPlayerManager.loadItemOrdered(musicManager, trackURL, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                musicManager.scheduler.queue(audioTrack);

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🎶 Добавлено в очередь");
                embed.setDescription(audioTrack.getInfo().title);
                embed.setColor(Color.GREEN);
                embed.setThumbnail("https://i.ytimg.com/vi/" + audioTrack.getInfo().identifier + "/hqdefault.jpg");

                textChannel.sendMessageEmbeds(embed.build()).queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                final List<AudioTrack> tracks = audioPlaylist.getTracks();

                if (!tracks.isEmpty()) {
                    musicManager.scheduler.queue(tracks.get(0));

                    // Создаем EmbedBuilder для красивого оформления
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("🎶 Добавлено в очередь");
                    embed.setDescription("Трек **" + tracks.get(0).getInfo().title + "** от **" + tracks.get(0).getInfo().author + "**.");
                    embed.setColor(Color.GREEN);
                    embed.setThumbnail("https://i.ytimg.com/vi/" + tracks.get(0).getInfo().identifier + "/hqdefault.jpg");

                    textChannel.sendMessageEmbeds(embed.build()).queue();
                } else {
                    textChannel.sendMessage("Плейлист не был найден. Убедитесь, что вы ввели название без ошибок.").queue();
                }
            }

            @Override
            public void noMatches() {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("❌ Ошибка");
                embed.setDescription("Песня не была найдена. Убедитесь, что вы ввели название без ошибок.");
                embed.setColor(Color.RED);
                textChannel.sendMessageEmbeds(embed.build()).queue();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("❌ Неизвестная ошибка");
                embed.setDescription("Произошла ошибка при загрузке трека. Попробуйте снова позже.");
                embed.setColor(Color.RED);
                textChannel.sendMessageEmbeds(embed.build()).queue();
            }
        }); // <-- Закрывающая скобка для loadItemOrdered
    }

    /**
     * Получает экземпляр {@link PlayerManager}.
     *
     * @return экземпляр {@link PlayerManager}
     */
    public static PlayerManager getINSTANCE() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final PlayerManager INSTANCE = new PlayerManager();
    }
}
