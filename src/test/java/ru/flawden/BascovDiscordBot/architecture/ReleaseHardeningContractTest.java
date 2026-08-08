package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseHardeningContractTest {

    @Test
    void deliveryEmbedsRevisionAndVerifiesPublishedDigest() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));

        assertTrue(workflow.contains("APP_REVISION=${{ github.sha }}"));
        assertTrue(workflow.contains("BOT_IMAGE_DIGEST: ${{ needs.publish.outputs.digest }}"));
        assertTrue(workflow.contains("persist-credentials: false"));
        assertTrue(dockerfile.contains("-Dbuild.revision=\"${APP_REVISION}\""));
        assertTrue(deploy.contains("BOT_IMAGE_DIGEST:?BOT_IMAGE_DIGEST is missing"));
        assertTrue(deploy.contains("RepoDigests"));
        assertTrue(deploy.contains("Persistence readiness: READY"));
    }

    @Test
    void productionDeliveryRemainsGitHubHosted() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));

        assertTrue(workflow.contains("runs-on: ubuntu-latest"));
        assertFalse(workflow.contains("runs-on: [self-hosted, linux, x64]"));
    }
}
