package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;

/**
 * Менеджер музыки для гильдии, обеспечивающий взаимодействие с
 * {@link AudioPlayer} и {@link TrackScheduler}.
 * Этот класс управляет воспроизведением музыки в гильдии и
 * предоставляет доступ к {@link AudioPlayerSendHandler} для отправки
 * аудиоданных в Discord.
 *
 * @author Flawden
 * @version 1.0
 */
@Slf4j
@Getter
public class GuildMusicManager {

    public final AudioPlayer audioPlayer;
    public final TrackScheduler scheduler;
    /**
     * -- GETTER --
     *  Получает обработчик отправки аудио.
     *
     * @return экземпляр {@link AudioPlayerSendHandler}
     */
    private final AudioPlayerSendHandler sendHandler;


    public GuildMusicManager(AudioPlayerManager manager, Guild guild) {
        this.audioPlayer = manager.createPlayer();
        this.scheduler = new TrackScheduler(this.audioPlayer, guild);
        this.audioPlayer.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.audioPlayer);
        log.info("GuildMusicManager created with AudioPlayer: {}", audioPlayer);
    }

}
