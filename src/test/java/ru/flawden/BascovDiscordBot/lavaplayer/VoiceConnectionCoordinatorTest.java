package ru.flawden.BascovDiscordBot.lavaplayer;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.config.MusicProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceConnectionCoordinatorTest {

    private VoiceConnectionCoordinator coordinator;

    @AfterEach
    void closeCoordinator() {
        if (coordinator != null) {
            coordinator.close();
        }
    }

    @Test
    void disablesAutomaticReconnectAndTimesOutBoundedAttempt() throws Exception {
        MusicProperties properties = new MusicProperties();
        properties.setVoiceConnectTimeout(Duration.ofMillis(100));
        properties.setVoiceFailureCooldown(Duration.ofSeconds(1));
        coordinator = new VoiceConnectionCoordinator(properties, Clock.systemUTC());
        Fixture fixture = fixture(false, false);

        VoiceConnectionResult result = coordinator.ensureConnected(
                        fixture.guild(), fixture.channel(), fixture.sendHandler())
                .get(2, TimeUnit.SECONDS);

        assertEquals(VoiceConnectionResult.Status.TIMEOUT, result.status());
        verify(fixture.audioManager()).setAutoReconnect(false);
        verify(fixture.audioManager()).openAudioConnection(fixture.channel());
        verify(fixture.audioManager()).closeAudioConnection();
    }

    @Test
    void concurrentRequestsShareOneAttemptPerGuildAndChannel() {
        MusicProperties properties = new MusicProperties();
        properties.setVoiceConnectTimeout(Duration.ofSeconds(5));
        coordinator = new VoiceConnectionCoordinator(properties, Clock.systemUTC());
        Fixture fixture = fixture(false, false);

        var first = coordinator.ensureConnected(
                fixture.guild(), fixture.channel(), fixture.sendHandler());
        var second = coordinator.ensureConnected(
                fixture.guild(), fixture.channel(), fixture.sendHandler());

        assertSame(first, second);
        verify(fixture.audioManager()).openAudioConnection(fixture.channel());
        coordinator.cancel(fixture.guild());
    }

    @Test
    void alreadyConnectedChannelDoesNotOpenAgain() throws Exception {
        MusicProperties properties = new MusicProperties();
        coordinator = new VoiceConnectionCoordinator(properties, Clock.systemUTC());
        Fixture fixture = fixture(true, true);

        VoiceConnectionResult result = coordinator.ensureConnected(
                        fixture.guild(), fixture.channel(), fixture.sendHandler())
                .get(1, TimeUnit.SECONDS);

        assertEquals(VoiceConnectionResult.Status.CONNECTED, result.status());
        verify(fixture.audioManager(), never()).openAudioConnection(fixture.channel());
        verify(fixture.audioManager()).setSendingHandler(fixture.sendHandler());
    }

    private static Fixture fixture(boolean audioConnected, boolean voiceStateConnected) {
        Guild guild = mock(Guild.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioChannelUnion channel = mock(AudioChannelUnion.class);
        AudioPlayerSendHandler sendHandler = mock(AudioPlayerSendHandler.class);
        Member selfMember = mock(Member.class);
        GuildVoiceState voiceState = mock(GuildVoiceState.class);

        when(guild.getIdLong()).thenReturn(42L);
        when(guild.getId()).thenReturn("42");
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(selfMember.getVoiceState()).thenReturn(voiceState);
        when(channel.getIdLong()).thenReturn(99L);
        when(channel.getId()).thenReturn("99");
        when(audioManager.isConnected()).thenReturn(audioConnected);
        when(voiceState.inAudioChannel()).thenReturn(voiceStateConnected);
        when(voiceState.getChannel()).thenReturn(voiceStateConnected ? channel : null);

        return new Fixture(guild, audioManager, channel, sendHandler);
    }

    private record Fixture(
            Guild guild,
            AudioManager audioManager,
            AudioChannel channel,
            AudioPlayerSendHandler sendHandler) {
    }
}
