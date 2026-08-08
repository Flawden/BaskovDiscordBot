package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfHostedDeliveryContractTest {

    @Test
    void deliveryJobsTargetLinuxSelfHostedRunner() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));

        assertTrue(workflow.contains("runs-on: [self-hosted, linux, x64]"));
        assertFalse(workflow.contains("runs-on: ubuntu-latest"));
        assertTrue(workflow.contains("Report self-hosted runner"));
    }

    @Test
    void deploySshCredentialsAreEphemeralOnPersistentRunner() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));

        assertTrue(workflow.contains("${RUNNER_TEMP}/baskov-ssh."));
        assertTrue(workflow.contains("BASKOV_SSH_KEY"));
        assertTrue(workflow.contains("Remove ephemeral SSH credentials"));
        assertFalse(workflow.contains("~/.ssh/id_ed25519"));
    }
}
