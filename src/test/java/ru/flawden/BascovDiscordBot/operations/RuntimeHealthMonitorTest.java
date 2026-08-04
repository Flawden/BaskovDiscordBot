package ru.flawden.BascovDiscordBot.operations;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeHealthMonitorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesConnectedHeartbeatAndRemovesItOnClose() throws Exception {
        Path healthFile = tempDirectory.resolve("ready");
        JDA jda = mock(JDA.class);
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(jda.getGuilds()).thenReturn(List.of());

        RuntimeHealthMonitor monitor = new RuntimeHealthMonitor(healthFile);
        monitor.start(jda, 18);

        String payload = Files.readString(healthFile);
        assertTrue(payload.contains("status=CONNECTED"));
        assertTrue(payload.contains("slashCommands=18"));

        monitor.close();
        assertFalse(Files.exists(healthFile));
    }
}
