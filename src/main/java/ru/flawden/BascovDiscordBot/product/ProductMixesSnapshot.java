package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.home.HomeSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Client-neutral mix catalog derived from the same product-home state used by Discord. */
public record ProductMixesSnapshot(
        long guildId,
        long userId,
        LocalDate date,
        Optional<HomeSnapshot.ContinuationCard> continuation,
        List<HomeSnapshot.MixCard> today,
        List<HomeSnapshot.MixCard> forYou,
        List<HomeSnapshot.ThemeCard> themes) {

    public ProductMixesSnapshot {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        continuation = continuation == null ? Optional.empty() : continuation;
        today = List.copyOf(today == null ? List.of() : today);
        forYou = List.copyOf(forYou == null ? List.of() : forYou);
        themes = List.copyOf(themes == null ? List.of() : themes);
    }
}
