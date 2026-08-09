package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationFeedbackPropertiesTest {

    @Test
    void defaultsToPersistentDataDirectory() {
        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        assertEquals(Path.of("data", "recommendation-feedback.tsv"), properties.getFile());
    }
}
