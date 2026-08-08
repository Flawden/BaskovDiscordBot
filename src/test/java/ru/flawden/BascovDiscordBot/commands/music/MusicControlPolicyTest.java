package ru.flawden.BascovDiscordBot.commands.music;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.settings.PlaybackAccessMode;
import ru.flawden.BascovDiscordBot.settings.RequestAccessMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void requestAccessCanBeRestrictedToDjAndConfiguredVoiceChannel() {
        assertTrue(MusicControlPolicy.requestDecision(
                RequestAccessMode.DJ_ONLY, false, true, 55L, 55L, null).allowed());
        assertFalse(MusicControlPolicy.requestDecision(
                RequestAccessMode.DJ_ONLY, false, false, 55L, 55L, null).allowed());
        assertFalse(MusicControlPolicy.requestDecision(
                RequestAccessMode.OPEN, true, false, 55L, 99L, null).allowed());
        assertTrue(MusicControlPolicy.requestDecision(
                RequestAccessMode.OPEN, false, false, 0L, 99L, null).allowed());
    }

    @Test
    void configuredVoiceChannelAlsoProtectsAnExistingSession() {
        assertFalse(MusicControlPolicy.requestDecision(
                RequestAccessMode.OPEN, true, false, 55L, 55L, 99L).allowed());
        assertTrue(MusicControlPolicy.requestDecision(
                RequestAccessMode.OPEN, false, false, 55L, 55L, 55L).allowed());
    }

    @Test
    void djOnlyModeGrantsConfiguredDjButDeniesOrdinaryListener() {
        assertTrue(MusicControlPolicy.controlDecision(
                PlaybackAccessMode.DJ_ONLY, false, true, 10L, 10L).allowed());
        assertFalse(MusicControlPolicy.controlDecision(
                PlaybackAccessMode.DJ_ONLY, false, false, 10L, 10L).allowed());
    }

    @Test
    void voteModeRoutesOrdinarySkipToBallotAndDjSkipDirectly() {
        assertEquals(MusicControlPolicy.SkipAccess.VOTE,
                MusicControlPolicy.skipDecision(
                        PlaybackAccessMode.VOTE_SKIP, false, false, 10L, 10L).access());
        assertEquals(MusicControlPolicy.SkipAccess.DIRECT,
                MusicControlPolicy.skipDecision(
                        PlaybackAccessMode.VOTE_SKIP, false, true, 10L, 10L).access());
        assertEquals(MusicControlPolicy.SkipAccess.DIRECT,
                MusicControlPolicy.skipDecision(
                        PlaybackAccessMode.VOTE_SKIP, true, false, null, 10L).access());
    }

    @Test
    void openModePreservesExistingDirectControl() {
        assertTrue(MusicControlPolicy.controlDecision(
                PlaybackAccessMode.OPEN, false, false, 10L, 10L).allowed());
        assertEquals(MusicControlPolicy.SkipAccess.DIRECT,
                MusicControlPolicy.skipDecision(
                        PlaybackAccessMode.OPEN, false, false, 10L, 10L).access());
    }
}
