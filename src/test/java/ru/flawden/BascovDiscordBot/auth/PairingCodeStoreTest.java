package ru.flawden.BascovDiscordBot.auth;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.config.AuthProperties;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PairingCodeStoreTest {
    @Test void codeIsOneTimeAndUserBound() {
        AuthProperties p=new AuthProperties(); p.setPairingTtl(Duration.ofMinutes(5));
        PairingCodeStore store=new PairingCodeStore(p,Clock.fixed(Instant.parse("2026-08-10T14:00:00Z"),ZoneId.of("UTC")),new SecureRandom());
        var grant=store.issue(42L,"Alex");
        assertEquals(8,grant.code().length());
        assertEquals(42L,store.consume(grant.code()).orElseThrow().discordUserId());
        assertTrue(store.consume(grant.code()).isEmpty());
    }
    @Test void issuingSecondCodeInvalidatesPreviousForSameDiscordUser() {
        AuthProperties p=new AuthProperties(); PairingCodeStore store=new PairingCodeStore(p,Clock.systemUTC(),new SecureRandom());
        var first=store.issue(42L,"A"); var second=store.issue(42L,"A");
        assertTrue(store.consume(first.code()).isEmpty());
        assertTrue(store.consume(second.code()).isPresent());
    }
    @Test void normalizationAcceptsSpacesAndDashes() {
        assertEquals("ABCD2345",PairingCodeStore.normalize(" abcd-2345 "));
    }

    @Test void expiredCodeCannotBeConsumed() {
        AuthProperties p=new AuthProperties(); p.setPairingTtl(Duration.ofSeconds(10));
        MutableClock clock=new MutableClock(Instant.parse("2026-08-10T14:00:00Z"));
        PairingCodeStore store=new PairingCodeStore(p,clock,new SecureRandom());
        var grant=store.issue(42L,"Alex");
        clock.advance(Duration.ofSeconds(11));
        assertTrue(store.consume(grant.code()).isEmpty());
    }
    private static final class MutableClock extends Clock {
        private Instant instant; MutableClock(Instant instant){this.instant=instant;}
        void advance(Duration d){instant=instant.plus(d);} public ZoneId getZone(){return ZoneId.of("UTC");}
        public Clock withZone(ZoneId zone){return this;} public Instant instant(){return instant;}
    }
}
