package ru.flawden.BascovDiscordBot.home;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.MixDiversityProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds the personalized product-home read model independently of any client UI. */
@Component
public class MusicHomeService {

    private static final int MAX_THEMES = 5;
    private static final int MAX_RECENT = 3;

    private final MusicHomeReadPort source;

    public MusicHomeService(MusicHomeReadPort source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public HomeSnapshot snapshot(long guildId, long userId) {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<StoredTrack> favorites = source.favorites(guildId, userId);
        List<StoredTrack> history = source.personalHistory(guildId, userId);
        PersonalTasteProfile taste = source.tasteProfile(guildId, userId);

        return new HomeSnapshot(
                guildId,
                userId,
                today,
                continuation(guildId, userId),
                List.of(
                        mixCard(guildId, userId, PersonalizedStation.DAILY_MIX),
                        mixCard(guildId, userId, PersonalizedStation.DAILY_DISCOVERIES)),
                List.of(
                        mixCard(guildId, userId, PersonalizedStation.MY_MIX),
                        mixCard(guildId, userId, PersonalizedStation.MOOD),
                        mixCard(guildId, userId, PersonalizedStation.DISCOVERIES),
                        mixCard(guildId, userId, PersonalizedStation.FAMILIAR)),
                themes(taste),
                new HomeSnapshot.LibraryCard(favorites.size(), history.size()),
                history.stream()
                        .limit(MAX_RECENT)
                        .map(track -> new HomeSnapshot.TrackPreview(track.title(), track.author()))
                        .toList(),
                new HomeSnapshot.TasteCard(
                        taste.evidenceSignals(),
                        taste.confidence(),
                        taste.recommendations()));
    }

    private Optional<HomeSnapshot.ContinuationCard> continuation(long guildId, long userId) {
        Optional<MusicHomeReadPort.StationState> active = source.activeStation(guildId, userId);
        if (active.isPresent()) {
            return active.map(state -> continuationCard(HomeSnapshot.ContinuationCard.Kind.ACTIVE, state));
        }
        return source.resumableStation(guildId, userId)
                .map(state -> continuationCard(HomeSnapshot.ContinuationCard.Kind.RESUMABLE, state));
    }

    private static HomeSnapshot.ContinuationCard continuationCard(
            HomeSnapshot.ContinuationCard.Kind kind,
            MusicHomeReadPort.StationState state) {
        return new HomeSnapshot.ContinuationCard(
                kind,
                state.station().label(),
                state.station().slug(),
                state.theme(),
                state.releaseDate(),
                state.generatedTracks());
    }

    private HomeSnapshot.MixCard mixCard(long guildId, long userId, PersonalizedStation station) {
        return new HomeSnapshot.MixCard(
                station.slug(),
                station.label(),
                station.description(),
                source.hasStationSeeds(guildId, userId, station),
                station.dailySeeded());
    }

    private static List<HomeSnapshot.ThemeCard> themes(PersonalTasteProfile taste) {
        return taste.tagAffinity().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0.0d)
                .sorted(MusicHomeService::compareAffinity)
                .map(entry -> new HomeSnapshot.ThemeCard(
                        MixDiversityProfile.normalizeTheme(entry.getKey()),
                        entry.getValue()))
                .filter(theme -> !theme.name().isBlank())
                .distinct()
                .limit(MAX_THEMES)
                .toList();
    }

    private static int compareAffinity(Map.Entry<String, Double> left, Map.Entry<String, Double> right) {
        int byScore = Double.compare(right.getValue(), left.getValue());
        return byScore != 0 ? byScore : left.getKey().compareTo(right.getKey());
    }
}
