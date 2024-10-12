package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Обработчик отправки аудио для взаимодействия с Discord Audio API.
 * Этот класс реализует интерфейс {@link AudioSendHandler} и отвечает за
 * передачу аудиоданных из {@link AudioPlayer} в канал Discord.
 *
 * @author Flawden
 * @version 1.0
 */
public class AudioPlayerSendHandler implements AudioSendHandler {

    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    /**
     * Проверяет, может ли обработчик предоставить аудиоданные.
     *
     * @return {@code true}, если аудиоданные могут быть предоставлены, иначе {@code false}
     */
    @Override
    public boolean canProvide() {
        return this.audioPlayer.provide(this.frame);
    }

    /**
     * Предоставляет 20 миллисекунд аудиоданных в формате {@link ByteBuffer}.
     *
     * @return {@link ByteBuffer} с аудиоданными или {@code null}, если данные отсутствуют
     */
    @Nullable
    @Override
    public ByteBuffer provide20MsAudio() {
        final Buffer buffer = ((Buffer) this.buffer).flip();
        return (ByteBuffer) buffer;
    }

    /**
     * Указывает, используется ли кодек Opus для передачи аудио.
     *
     * @return {@code true}, если используется кодек Opus, иначе {@code false}
     */
    @Override
    public boolean isOpus() {
        return true;
    }
}
