package ru.flawden.BascovDiscordBot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "discordBot.enabled=false")
class BascovDiscordBotApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithoutConnectingToDiscord() {
        assertNotNull(applicationContext);
    }
}
