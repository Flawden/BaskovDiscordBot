package ru.flawden.BascovDiscordBot.commands;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionEventTest {

    @Test
    void resolvesVersionFromMavenBuildInfo() {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        VersionEvent event = new VersionEvent(provider);
        Properties properties = new Properties();
        properties.setProperty("version", "0.2.0");

        assertEquals("0.2.0", event.resolveVersion(new BuildProperties(properties)));
    }

    @Test
    void fallsBackToDevelopmentOutsidePackagedBuild() {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        VersionEvent event = new VersionEvent(provider);

        assertEquals("development", event.resolveVersion(null));
    }
}
