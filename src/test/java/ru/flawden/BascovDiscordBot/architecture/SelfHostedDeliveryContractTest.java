package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubHostedDeliveryContractTest {

    @Test
    void deliveryJobsTargetGitHubHostedLinuxRunner() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));

        assertTrue(workflow.contains("runs-on: ubuntu-latest"));
        assertFalse(workflow.contains("runs-on: [self-hosted, linux, x64]"));
        assertTrue(workflow.contains("Report GitHub-hosted runner"));
    }

    @Test
    void deploySshCredentialsRemainEphemeralOnHostedRunner() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));

        assertTrue(workflow.contains("${RUNNER_TEMP}/baskov-ssh."));
        assertTrue(workflow.contains("BASKOV_SSH_KEY"));
        assertTrue(workflow.contains("Remove ephemeral SSH credentials"));
        assertFalse(workflow.contains("~/.ssh/id_ed25519"));
    }
}
