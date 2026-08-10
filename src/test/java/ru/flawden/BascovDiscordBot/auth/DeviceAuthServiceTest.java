package ru.flawden.BascovDiscordBot.auth;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.config.AuthProperties;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeviceAuthServiceTest {
    @Test void pairingCreatesProviderNeutralUserAndHashedDeviceSession() {
        Fixture f=new Fixture(); var grant=f.service.issueDiscordPairing(42L,"Alex");
        var tokens=f.service.pairDevice(grant.code(),"Pixel 9");
        assertTrue(tokens.accessToken().startsWith("bka_")); assertTrue(tokens.refreshToken().startsWith("bkr_"));
        assertEquals(42L,tokens.principal().discordUserId());
        DeviceSession stored=f.repo.sessions(tokens.principal().userId()).get(0);
        assertNotEquals(tokens.accessToken(),stored.accessTokenHash());
        assertEquals(DeviceAuthService.hash(tokens.accessToken()),stored.accessTokenHash());
        assertTrue(f.repo.findUserByIdentity(IdentityProvider.DISCORD,"42").isPresent());
    }
    @Test void sameDiscordIdentityReusesBaskovUserAcrossDevices() {
        Fixture f=new Fixture(); var first=f.service.pairDevice(f.service.issueDiscordPairing(42L,"Alex").code(),"Phone");
        var second=f.service.pairDevice(f.service.issueDiscordPairing(42L,"Alex").code(),"Tablet");
        assertEquals(first.principal().userId(),second.principal().userId());
        assertNotEquals(first.principal().sessionId(),second.principal().sessionId());
    }
    @Test void refreshRotatesBothTokensAndInvalidatesOldRefresh() {
        Fixture f=new Fixture(); var first=f.service.pairDevice(f.service.issueDiscordPairing(42L,"Alex").code(),"Phone");
        var rotated=f.service.refresh(first.refreshToken());
        assertNotEquals(first.accessToken(),rotated.accessToken()); assertNotEquals(first.refreshToken(),rotated.refreshToken());
        assertThrows(DeviceAuthService.AuthException.class,()->f.service.refresh(first.refreshToken()));
        assertEquals(42L,f.service.authenticateBearer(rotated.accessToken()).discordUserId());
    }
    @Test void revokingOneDeviceDoesNotRevokeAnother() {
        Fixture f=new Fixture(); var first=f.service.pairDevice(f.service.issueDiscordPairing(42L,"Alex").code(),"Phone");
        var second=f.service.pairDevice(f.service.issueDiscordPairing(42L,"Alex").code(),"Tablet");
        f.service.revokeDevice(first.accessToken(),first.principal().sessionId());
        assertThrows(DeviceAuthService.AuthException.class,()->f.service.authenticateBearer(first.accessToken()));
        assertEquals(second.principal().sessionId(),f.service.authenticateBearer(second.accessToken()).sessionId());
    }
    @Test void maxActiveDevicesIsEnforced() {
        Fixture f=new Fixture(); f.props.setMaxDeviceSessions(1);
        f.service.pairDevice(f.service.issueDiscordPairing(42L,"Alex").code(),"Phone");
        var grant=f.service.issueDiscordPairing(42L,"Alex");
        DeviceAuthService.AuthException ex=assertThrows(DeviceAuthService.AuthException.class,()->f.service.pairDevice(grant.code(),"Tablet"));
        assertEquals("DEVICE_LIMIT",ex.code());
    }

    @Test void expiredAccessTokenRequiresRefresh() {
        AuthProperties props=new AuthProperties(); props.setAccessTokenTtl(Duration.ofSeconds(5));
        MutableClock clock=new MutableClock(Instant.parse("2026-08-10T14:00:00Z")); InMemoryRepo repo=new InMemoryRepo();
        PairingCodeStore pair=new PairingCodeStore(props,clock,new SecureRandom()); DeviceAuthService service=new DeviceAuthService(repo,pair,props,clock,new SecureRandom());
        var tokens=service.pairDevice(service.issueDiscordPairing(42L,"Alex").code(),"Phone");
        clock.advance(Duration.ofSeconds(6));
        assertEquals("ACCESS_TOKEN_EXPIRED",assertThrows(DeviceAuthService.AuthException.class,()->service.authenticateBearer(tokens.accessToken())).code());
        assertEquals(42L,service.refresh(tokens.refreshToken()).principal().discordUserId());
    }

    @Test void bearerAuthenticationRejectsUnknownToken() {
        Fixture f=new Fixture(); assertEquals("ACCESS_TOKEN_INVALID",assertThrows(DeviceAuthService.AuthException.class,()->f.service.authenticateBearer("bka_missing")).code());
    }


    private static final class MutableClock extends Clock {
        private Instant instant; MutableClock(Instant instant){this.instant=instant;}
        void advance(Duration duration){instant=instant.plus(duration);} public ZoneId getZone(){return ZoneId.of("UTC");}
        public Clock withZone(ZoneId zone){return this;} public Instant instant(){return instant;}
    }

    private static final class Fixture {
        final AuthProperties props=new AuthProperties(); final InMemoryRepo repo=new InMemoryRepo(); final DeviceAuthService service;
        Fixture(){ props.setAccessTokenTtl(Duration.ofMinutes(30)); props.setRefreshTokenTtl(Duration.ofDays(30)); PairingCodeStore pair=new PairingCodeStore(props,Clock.fixed(Instant.parse("2026-08-10T14:00:00Z"),ZoneId.of("UTC")),new SecureRandom()); service=new DeviceAuthService(repo,pair,props,Clock.fixed(Instant.parse("2026-08-10T14:00:00Z"),ZoneId.of("UTC")),new SecureRandom()); }
    }
    private static final class InMemoryRepo implements AuthRepository {
        final Map<String,BaskovUser> users=new LinkedHashMap<>(); final Map<String,ExternalIdentity> identities=new LinkedHashMap<>(); final Map<String,DeviceSession> sessions=new LinkedHashMap<>();
        public Optional<BaskovUser> findUser(String id){return Optional.ofNullable(users.get(id));}
        public Optional<BaskovUser> findUserByIdentity(IdentityProvider p,String s){ExternalIdentity i=identities.get(p+":"+s);return i==null?Optional.empty():findUser(i.userId());}
        public BaskovUser saveUser(BaskovUser u){users.put(u.userId(),u);return u;}
        public ExternalIdentity linkIdentity(ExternalIdentity i){identities.put(i.provider()+":"+i.subject(),i);return i;}
        public Optional<ExternalIdentity> findIdentity(String userId,IdentityProvider p){return identities.values().stream().filter(i->i.userId().equals(userId)&&i.provider()==p).findFirst();}
        public DeviceSession saveSession(DeviceSession s){sessions.put(s.sessionId(),s);return s;}
        public Optional<DeviceSession> findSessionByAccessHash(String h){return sessions.values().stream().filter(s->s.accessTokenHash().equals(h)).findFirst();}
        public Optional<DeviceSession> findSessionByRefreshHash(String h){return sessions.values().stream().filter(s->s.refreshTokenHash().equals(h)).findFirst();}
        public Optional<DeviceSession> findSession(String id){return Optional.ofNullable(sessions.get(id));}
        public List<DeviceSession> sessions(String uid){return sessions.values().stream().filter(s->s.userId().equals(uid)).toList();}
        public DeviceSession revokeSession(String id,long at){DeviceSession r=sessions.get(id).revoke(at);sessions.put(id,r);return r;}
    }
}
