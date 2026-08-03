package ru.flawden.BascovDiscordBot.commands.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicControlPolicyTest {

    @Test
    void ordinaryMemberMustShareBotsVoiceChannel() {
        assertTrue(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.CONTROL_PLAYBACK, false, 10L, 10L).allowed());
        assertFalse(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.CONTROL_PLAYBACK, false, 11L, 10L).allowed());
        assertFalse(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.CONTROL_PLAYBACK, false, null, 10L).allowed());
    }

    @Test
    void administratorCanControlExistingSessionFromAnotherChannel() {
        assertTrue(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.CONTROL_PLAYBACK, true, null, 10L).allowed());
        assertTrue(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.START_OR_QUEUE, true, 11L, 10L).allowed());
    }

    @Test
    void startingNewSessionAlwaysRequiresAUsersVoiceChannel() {
        assertFalse(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.START_OR_QUEUE, true, null, null).allowed());
        assertTrue(MusicControlPolicy.decide(
                MusicControlPolicy.Mode.START_OR_QUEUE, false, 10L, null).allowed());
    }
}
