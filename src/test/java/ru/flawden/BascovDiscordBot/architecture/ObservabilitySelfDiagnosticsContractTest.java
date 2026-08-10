package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilitySelfDiagnosticsContractTest {

    @Test
    void doctorAggregatesExistingRuntimeSignalsWithoutExternalNetworkProbe() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/operations/SystemDoctor.java"));

        assertTrue(source.contains("healthMonitor.snapshot()"));
        assertTrue(source.contains("persistenceReadiness.probe()"));
        assertTrue(source.contains("persistenceBackupService.snapshot()"));
        assertTrue(source.contains("voiceDiagnosticsSnapshot(guild)"));
        assertTrue(source.contains("sessionRecoverySnapshot()"));
        assertTrue(source.contains("operationalMetrics.snapshot()"));
        assertFalse(source.contains("HttpClient"));
        assertFalse(source.contains("URLConnection"));
    }

    @Test
    void doctorExposesActionableSeverityAndNextStep() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/operations/SystemDoctor.java"));

        assertTrue(source.contains("enum Severity"));
        assertTrue(source.contains("String action"));
        assertTrue(source.contains("Severity.OK"));
        assertTrue(source.contains("Severity.WARN"));
        assertTrue(source.contains("Severity.FAIL"));
    }

    @Test
    void commandFailureJournalIsBoundedAndPrivacySafeAtInteractionBoundary() throws Exception {
        String metrics = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/operations/OperationalMetrics.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));

        assertTrue(metrics.contains("MAX_RECENT_FAILURES = 25"));
        assertTrue(metrics.contains("recentFailures.removeLast()"));
        assertTrue(interactions.contains("experienceButton ? \"experience-button\" : \"music-button\""));
        assertFalse(interactions.contains("recordFailure(OperationalMetrics.Channel.BUTTON, event.getComponentId()"));
    }

    @Test
    void doctorIsReadOnlyAndDoesNotReuseSessionRecoveryMutation() throws Exception {
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));
        int start = interactions.indexOf("private void doctor(SlashCommandInteractionEvent event)");
        int end = interactions.indexOf("private MessageEmbed doctorEmbed(", start);
        String doctor = interactions.substring(start, end);

        assertTrue(doctor.contains("systemDoctor.diagnose(event.getGuild())"));
        assertFalse(doctor.contains("retryPersistedSession"));
        assertFalse(doctor.contains("save"));
        assertFalse(doctor.contains("remove"));
    }
}
