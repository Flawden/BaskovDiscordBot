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
        ObjectProvider<BuildProperties> provider = mockBuildPropertiesProvider();
        VersionEvent event = new VersionEvent(provider);
        Properties properties = new Properties();
        properties.setProperty("version", "0.4.3");

        assertEquals("0.4.3", event.resolveVersion(new BuildProperties(properties)));
    }

    @Test
    void resolvesRevisionFromBuildInfo() {
        ObjectProvider<BuildProperties> provider = mockBuildPropertiesProvider();
        VersionEvent event = new VersionEvent(provider);
        Properties properties = new Properties();
        properties.setProperty("revision", "0123456789abcdef");

        assertEquals("0123456789abcdef", event.resolveRevision(new BuildProperties(properties)));
    }

    @Test
    void fallsBackToDevelopmentOutsidePackagedBuild() {
        ObjectProvider<BuildProperties> provider = mockBuildPropertiesProvider();
        when(provider.getIfAvailable()).thenReturn(null);
        VersionEvent event = new VersionEvent(provider);

        assertEquals("development", event.resolveVersion(null));
        assertEquals("development", event.resolveRevision(null));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<BuildProperties> mockBuildPropertiesProvider() {
        return (ObjectProvider<BuildProperties>) mock(ObjectProvider.class);
    }
}
