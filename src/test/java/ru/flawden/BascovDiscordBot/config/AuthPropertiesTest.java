package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class AuthPropertiesTest {
    @Test void defaultsAreBoundedAndSafe(){AuthProperties p=new AuthProperties();assertEquals(Path.of("data","baskov-auth.tsv"),p.getFile());assertEquals(Duration.ofMinutes(5),p.getPairingTtl());assertEquals(Duration.ofMinutes(30),p.getAccessTokenTtl());assertEquals(Duration.ofDays(30),p.getRefreshTokenTtl());assertEquals(8,p.getMaxDeviceSessions());}
    @Test void rejectsUnsafeDeviceSessionLimits(){AuthProperties p=new AuthProperties();assertThrows(IllegalArgumentException.class,()->p.setMaxDeviceSessions(0));assertThrows(IllegalArgumentException.class,()->p.setMaxDeviceSessions(65));}
    @Test void rejectsNonPositiveTokenTtls(){AuthProperties p=new AuthProperties();assertThrows(IllegalArgumentException.class,()->p.setAccessTokenTtl(Duration.ZERO));assertThrows(IllegalArgumentException.class,()->p.setRefreshTokenTtl(Duration.ofSeconds(-1)));}
}
