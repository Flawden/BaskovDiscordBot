package ru.flawden.BascovDiscordBot.recommendation;

/**
 * Насколько далеко smart-radio разрешено отходить от уже знакомой библиотеки.
 */
public enum RadioStrategy {
    FAMILIAR("Знакомое", false, false),
    SIMILAR("Похожее", true, false),
    DISCOVERY("Новое", true, true);

    private final String label;
    private final boolean externalSimilarity;
    private final boolean hardNovelty;

    RadioStrategy(String label, boolean externalSimilarity, boolean hardNovelty) {
        this.label = label;
        this.externalSimilarity = externalSimilarity;
        this.hardNovelty = hardNovelty;
    }

    public String label() {
        return label;
    }

    public boolean externalSimilarity() {
        return externalSimilarity;
    }

    public boolean hardNovelty() {
        return hardNovelty;
    }
}
