package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Arrays;
import java.util.List;

/**
 * Product-level presets over the lower-level smart-radio controls.
 *
 * <p>The station never bypasses playback, queue, voice or novelty policy. It only
 * selects a radio strategy and a bounded seed policy.</p>
 */
public enum PersonalizedStation {
    CUSTOM("custom", "Ручное радио", "Ручная конфигурация /radio", RadioStrategy.SIMILAR, false, false),
    MY_MIX(
            "my-mix",
            "Мой микс",
            "Сбалансированное продолжение личного вкуса: favorites + history + все обученные ranking-сигналы.",
            RadioStrategy.SIMILAR,
            false,
            true),
    DISCOVERIES(
            "discoveries",
            "Открытия",
            "Новые треки с hard novelty: знакомые и недавние записи не возвращаются через ranking.",
            RadioStrategy.DISCOVERY,
            false,
            true),
    FAMILIAR(
            "familiar",
            "Знакомое",
            "Надёжное продолжение favorites/history без внешнего discovery-прыжка.",
            RadioStrategy.FAMILIAR,
            false,
            true),
    MOOD(
            "mood",
            "Настроение сейчас",
            "Стартует от самой свежей personal history и быстро адаптируется через session intelligence.",
            RadioStrategy.SIMILAR,
            true,
            true);

    private final String slug;
    private final String label;
    private final String description;
    private final RadioStrategy strategy;
    private final boolean recentSeedsOnly;
    private final boolean curated;

    PersonalizedStation(
            String slug,
            String label,
            String description,
            RadioStrategy strategy,
            boolean recentSeedsOnly,
            boolean curated) {
        this.slug = slug;
        this.label = label;
        this.description = description;
        this.strategy = strategy;
        this.recentSeedsOnly = recentSeedsOnly;
        this.curated = curated;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public RadioStrategy strategy() {
        return strategy;
    }

    public boolean recentSeedsOnly() {
        return recentSeedsOnly;
    }

    public boolean curated() {
        return curated;
    }

    public static PersonalizedStation fromSlug(String value) {
        if (value == null || value.isBlank()) {
            return MY_MIX;
        }
        return Arrays.stream(values())
                .filter(station -> station.curated && station.slug.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(MY_MIX);
    }

    public static List<PersonalizedStation> curatedStations() {
        return Arrays.stream(values()).filter(PersonalizedStation::curated).toList();
    }
}
