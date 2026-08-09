package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalMavenBootstrapContractTest {

    private static final String V150_KNOWN_GOOD_CACHE =
            "setup-java-Linux-x64-maven-71b8f0cd3bdce00d8b9dd28d9fd43a10b83fcfdfd9fe6875f2d0b3697ebd7207";

    @Test
    void workflowsMigrateKnownGoodV150CacheIntoAProjectOwnedStableCacheOnlyAfterVerification() throws Exception {
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String delivery = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String workflows = ci + "\n" + delivery;

        assertTrue(workflows.contains("actions/cache/restore@v4"));
        assertTrue(workflows.contains("actions/cache/save@v4"));
        assertTrue(workflows.contains(V150_KNOWN_GOOD_CACHE));
        assertTrue(workflows.contains("key: baskov-maven-${{ runner.os }}-${{ runner.arch }}-${{ hashFiles('.github/maven-cache-key.txt') }}"));
        assertTrue(workflows.contains("if: ${{ success() && steps.maven-cache.outputs.cache-hit != 'true' }}"));
        assertTrue(workflows.contains("key: ${{ steps.maven-cache.outputs.cache-primary-key }}"));
        assertFalse(workflows.contains("cache: maven"),
                "setup-java must not restore an immutable incomplete cache on top of the known-good seed");
    }

    @Test
    void bootstrapChecksEveryPinnedExternalPomAndJarAndPreservesOnlineFallback() throws Exception {
        String helper = Files.readString(Path.of(".github/scripts/maven-ci.sh"));

        assertTrue(helper.contains("dev/lavalink/youtube/v2/1.18.2/v2-1.18.2.pom"));
        assertTrue(helper.contains("dev/lavalink/youtube/v2/1.18.2/v2-1.18.2.jar"));
        assertTrue(helper.contains("moe/kyokobot/libdave/adapter-jda/ce725965e/adapter-jda-ce725965e.jar"));
        assertTrue(helper.contains("moe/kyokobot/libdave/impl-jni/ce725965e/impl-jni-ce725965e.jar"));
        assertTrue(helper.contains("moe/kyokobot/libdave/natives-linux-x86-64/ce725965e/natives-linux-x86-64-ce725965e.jar"));
        assertTrue(helper.contains("external Maven bootstrap:"));
        assertTrue(helper.contains("normal online fallback"));
        assertTrue(helper.contains("find \"$(dirname \"${full}\")\" -maxdepth 1 -name '*.lastUpdated'"));
    }

    @Test
    void productionImagePackagesTheJarThatAlreadyPassedMavenVerification() throws Exception {
        String delivery = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String helper = Files.readString(Path.of(".github/scripts/maven-ci.sh"));
        String dockerfile = Files.readString(Path.of("deploy/Dockerfile.ci"));
        String dockerignore = Files.readString(Path.of(".dockerignore"));

        assertTrue(delivery.contains("file: deploy/Dockerfile.ci"));
        assertTrue(ci.contains("--file deploy/Dockerfile.ci"));
        assertTrue(dockerfile.contains("COPY --chown=app:app target/baskov-discord-bot.jar /app/app.jar"));
        assertFalse(dockerfile.contains("mvnw"));
        assertFalse(dockerfile.contains("dependency:go-offline"));
        assertTrue(dockerignore.contains("!target/baskov-discord-bot.jar"));
        assertTrue(helper.contains("-Dbuild.revision=\"${MAVEN_BUILD_REVISION}\""));
        assertTrue(helper.contains("${GITHUB_SHA:-development}"));
    }
}
