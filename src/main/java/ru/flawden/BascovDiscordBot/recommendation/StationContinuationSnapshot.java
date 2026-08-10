package ru.flawden.BascovDiscordBot.recommendation;

import java.time.Instant;
import java.time.LocalDate;

/** Read-only product snapshot for an explicitly resumable curated station. */
public record StationContinuationSnapshot(
        PersonalizedStation station,
        LocalDate seedDate,
        String themeFocus,
        long generatedTracks,
        String lastTrack,
        Instant savedAt) {

    public StationContinuationSnapshot {
        station = station == null ? PersonalizedStation.CUSTOM : station;
        themeFocus = MixDiversityProfile.normalizeTheme(themeFocus);
        lastTrack = lastTrack == null || lastTrack.isBlank() ? "—" : lastTrack;
        savedAt = savedAt == null ? Instant.EPOCH : savedAt;
    }
}
