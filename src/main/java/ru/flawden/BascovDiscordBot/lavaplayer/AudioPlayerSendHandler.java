package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Передаёт Opus-фреймы в JDA без логирования каждого 20-мс аудиопакета.
 *
 * <p>Дополнительно хранит только монотонную отметку последнего запроса аудио.
 * Это позволяет watchdog отличать настоящий обрыв transport от краткого
 * переходного состояния {@code AudioManager.isConnected()}.</p>
 */
public class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;
    private final LongSupplier nanoTime;
    private final AtomicLong lastFrameRequestNanos = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong frameRequestCount = new AtomicLong();

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this(audioPlayer, System::nanoTime);
    }

    AudioPlayerSendHandler(AudioPlayer audioPlayer, LongSupplier nanoTime) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.buffer = ByteBuffer.allocate(2048);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        lastFrameRequestNanos.set(nanoTime.getAsLong());
        frameRequestCount.incrementAndGet();
        return audioPlayer.provide(frame);
    }

    @Nullable
    @Override
    public ByteBuffer provide20MsAudio() {
        Buffer flipped = buffer.flip();
        return (ByteBuffer) flipped;
    }

    @Override
    public boolean isOpus() {
        return true;
    }

    public boolean hasRecentFrameRequest(Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge");
        long last = lastFrameRequestNanos.get();
        if (last == Long.MIN_VALUE) {
            return false;
        }
        long elapsed = Math.max(0L, nanoTime.getAsLong() - last);
        return elapsed <= maxAge.toNanos();
    }

    public long frameRequestCount() {
        return frameRequestCount.get();
    }

    public Duration lastFrameRequestAge() {
        long last = lastFrameRequestNanos.get();
        if (last == Long.MIN_VALUE) {
            return null;
        }
        long elapsed = Math.max(0L, nanoTime.getAsLong() - last);
        return Duration.ofNanos(elapsed);
    }

    public void resetFrameTelemetry() {
        lastFrameRequestNanos.set(Long.MIN_VALUE);
        frameRequestCount.set(0L);
    }
}
