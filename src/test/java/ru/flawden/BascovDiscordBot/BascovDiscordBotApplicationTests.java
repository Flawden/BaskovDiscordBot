package ru.flawden.BascovDiscordBot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "discordBot.enabled=false")
class BascovDiscordBotApplicationTests {

	@Test
	void contextLoads() {
		assert(1 == 1);
	}

}
