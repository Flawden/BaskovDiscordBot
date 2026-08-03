package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandCoreContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void dispatcherDoesNotStoreSharedDiscordEvent() throws IOException {
        String source = read("config/eventconfig/BotEvents.java");

        assertFalse(source.contains("private MessageReceivedEvent event"));
        assertTrue(source.contains("new EventArgs(event, invocation)"));
    }

    @Test
    void botDoesNotRequestAllGatewayIntents() throws IOException {
        String source = read("config/BotConfig.java");

        assertFalse(source.contains("ALL_INTENTS"));
        assertTrue(source.contains("MESSAGE_CONTENT"));
        assertTrue(source.contains("GUILD_VOICE_STATES"));
    }

    @Test
    void mediaValidationDoesNotOpenUserUrlAndLocalSourceIsDisabled() throws IOException {
        String search = read("commands/music/MediaQueryResolver.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertFalse(search.contains("openStream"));
        assertFalse(player.contains("registerLocalSource"));
    }

    @Test
    void audioHotPathDoesNotWritePerFrameLogs() throws IOException {
        String source = read("lavaplayer/AudioPlayerSendHandler.java");

        assertFalse(source.contains("log.debug"));
        assertFalse(source.contains("log.trace"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
