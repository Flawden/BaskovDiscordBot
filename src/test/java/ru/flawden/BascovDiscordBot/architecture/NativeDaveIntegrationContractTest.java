package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeDaveIntegrationContractTest {

    @Test
    void pomPinsJniAdapterAndBothProductionDeveloperNatives() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String compact = compact(pom);

        assertTrue(compact.contains("<libdave-jvm.version>ce725965e</libdave-jvm.version>"));
        assertTrue(compact.contains("<id>lavalink-libdave-snapshots</id>"));
        assertTrue(compact.contains("<url>https://maven.lavalink.dev/snapshots</url>"));
        assertFalse(compact.contains("<id>jitpack</id>"));
        assertTrue(compact.contains("<artifactId>adapter-jda</artifactId>"));
        assertTrue(compact.contains("<artifactId>impl-jni</artifactId>"));
        assertTrue(compact.contains("<artifactId>natives-linux-x86-64</artifactId>"));
        assertTrue(compact.contains("<artifactId>natives-win-x86-64</artifactId>"));
        assertFalse(compact.contains(
                        "<groupId>club.minnced</groupId><artifactId>jdave-api</artifactId>"),
                "JDAVE requires Java 25 and must not enter the Java 17 release line");
    }

    @Test
    void libdaveUsesTheExactReleaseCommitSnapshotInsteadOfMissingTagArtifacts() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String runtime = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/dave/DaveRuntimeInfo.java"));
        String compactPom = compact(pom);

        assertTrue(compactPom.contains("<libdave-jvm.version>ce725965e</libdave-jvm.version>"));
        assertTrue(compactPom.contains("https://maven.lavalink.dev/snapshots"));
        assertFalse(compactPom.contains("<libdave-jvm.version>0.1.3</libdave-jvm.version>"));
        assertTrue(runtime.contains("IMPLEMENTATION_VERSION = \"ce725965e\""));
    }

    @Test
    void jdaAudioModuleUsesRealNativeFactoryInsteadOfPassthrough() throws Exception {
        String bootstrap = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/dave/NativeDaveBootstrap.java"));
        String botConfig = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/config/BotConfig.java"));
        String compactBootstrap = compact(bootstrap);
        String compactBotConfig = compact(botConfig);

        assertTrue(compactBootstrap.contains("NativeDaveFactory.ensureAvailable()"));
        assertTrue(compactBootstrap.contains("newLDJDADaveSessionFactory(nativeFactory)"));
        assertTrue(compactBootstrap.contains("maxProtocolVersion<=0"));
        assertTrue(compactBootstrap.contains("withDaveSessionFactory("));
        assertFalse(bootstrap.contains("PassthroughDaveSessionFactory"));
        assertTrue(compactBotConfig.contains("setAudioModuleConfig(audioModuleConfig)"));
    }

    @Test
    void statusAndTestsExposeNativeDaveReadiness() throws Exception {
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));
        String formatter = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/StatusMessageFormatter.java"));
        String smoke = Files.readString(Path.of(
                "src/test/java/ru/flawden/BascovDiscordBot/dave/NativeDaveRuntimeTest.java"));

        assertTrue(interactions.contains("DAVE / E2EE"));
        assertTrue(formatter.contains("Max protocol"));
        assertTrue(smoke.contains("NativeDaveFactory.ensureAvailable()"));
        assertTrue(smoke.contains("maxSupportedProtocolVersion()"));
    }

    @Test
    void deploymentRequiresTheNativeStartupMarker() throws Exception {
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));

        assertTrue(deploy.contains("Native libDAVE ready:"));
        assertTrue(deploy.contains("Native libDAVE startup marker is missing"));
        assertTrue(deploy.contains("verify_runtime \"${BOT_IMAGE}\" \"${BOT_IMAGE_DIGEST}\" true"));
        assertTrue(deploy.contains("verify_runtime \"${rollback_image}\" \"\" false"));
    }

    @Test
    void frameworkAndRuntimeBaselinesRemainIsolated() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertTrue(pom.contains("<java.version>17</java.version>"));
        assertTrue(pom.contains("<version>3.4.3</version>"));
        assertTrue(pom.contains("<version>6.5.0</version>"));
        assertTrue(pom.contains("<version>2.2.3</version>"));
        assertTrue(dockerfile.contains("eclipse-temurin:17-jre-jammy"));
        assertFalse(dockerfile.contains("temurin:25"));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }
}
