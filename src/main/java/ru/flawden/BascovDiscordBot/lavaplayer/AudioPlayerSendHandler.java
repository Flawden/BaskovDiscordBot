package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.Buffer;
import java.nio.ByteBuffer;

@Slf4j
public class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(2048); // Увеличиваем буфер до 2048 байт
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
        log.info("AudioPlayerSendHandler created for AudioPlayer: {}", audioPlayer);
    }

    @Override
    public boolean canProvide() {
        boolean canProvide = this.audioPlayer.provide(this.frame);
        log.debug("canProvide called, result: {}", canProvide);
        return canProvide;
    }

    @Nullable
    @Override
    public ByteBuffer provide20MsAudio() {
        log.trace("Providing 20ms audio, buffer position: {}", buffer.position());
        final Buffer buffer = ((Buffer) this.buffer).flip();
        return (ByteBuffer) buffer;
    }

    @Override
    public boolean isOpus() {
        log.trace("isOpus called, returning true");
        return true;
    }
}