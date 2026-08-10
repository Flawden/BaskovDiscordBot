package ru.flawden.BascovDiscordBot.auth;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.AuthProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pairing, token rotation, authentication and device revocation for Baskov clients. */
@Component
public class DeviceAuthService {
    private final AuthRepository repository;
    private final PairingCodeStore pairingCodes;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random;

    public DeviceAuthService(AuthRepository repository, PairingCodeStore pairingCodes, AuthProperties properties) {
        this(repository,pairingCodes,properties,Clock.systemUTC(),new SecureRandom());
    }
    DeviceAuthService(AuthRepository repository, PairingCodeStore pairingCodes, AuthProperties properties, Clock clock, SecureRandom random) {
        this.repository=Objects.requireNonNull(repository,"repository"); this.pairingCodes=Objects.requireNonNull(pairingCodes,"pairingCodes"); this.properties=Objects.requireNonNull(properties,"properties"); this.clock=Objects.requireNonNull(clock,"clock"); this.random=Objects.requireNonNull(random,"random");
    }

    public PairingCodeStore.PairingGrant issueDiscordPairing(long discordUserId,String displayName){ return pairingCodes.issue(discordUserId,displayName); }

    public synchronized TokenPair pairDevice(String code,String deviceName){
        String safeName=normalizeDeviceName(deviceName);
        PairingCodeStore.PairingGrant grant=pairingCodes.consume(code).orElseThrow(()->new AuthException("PAIRING_CODE_INVALID","Pairing code is invalid or expired"));
        BaskovUser user=repository.findUserByIdentity(IdentityProvider.DISCORD,Long.toUnsignedString(grant.discordUserId())).orElseGet(()->createDiscordUser(grant));
        long active=repository.sessions(user.userId()).stream().filter(s->!s.revoked()&&!s.refreshExpired(clock.millis())).count();
        if(active>=properties.getMaxDeviceSessions()) throw new AuthException("DEVICE_LIMIT","Too many active device sessions");
        return createSession(user,safeName);
    }

    public Principal authenticateBearer(String rawToken){
        String token=required(rawToken,"access token"); long now=clock.millis();
        DeviceSession session=repository.findSessionByAccessHash(hash(token)).orElseThrow(()->new AuthException("ACCESS_TOKEN_INVALID","Access token is invalid"));
        if(session.revoked()) throw new AuthException("SESSION_REVOKED","Device session is revoked");
        if(session.accessExpired(now)) throw new AuthException("ACCESS_TOKEN_EXPIRED","Access token has expired");
        return principal(session);
    }

    public synchronized TokenPair refresh(String rawRefreshToken){
        String token=required(rawRefreshToken,"refresh token"); long now=clock.millis();
        DeviceSession session=repository.findSessionByRefreshHash(hash(token)).orElseThrow(()->new AuthException("REFRESH_TOKEN_INVALID","Refresh token is invalid"));
        if(session.revoked()) throw new AuthException("SESSION_REVOKED","Device session is revoked");
        if(session.refreshExpired(now)) throw new AuthException("REFRESH_TOKEN_EXPIRED","Refresh token has expired");
        String access=token("bka_",32); String refresh=token("bkr_",48);
        DeviceSession rotated=session.rotate(hash(access),hash(refresh),now+properties.getAccessTokenTtl().toMillis(),now+properties.getRefreshTokenTtl().toMillis(),now);
        repository.saveSession(rotated);
        return response(principal(rotated),access,refresh,rotated);
    }

    public synchronized void logout(String rawAccessToken){ Principal p=authenticateBearer(rawAccessToken); repository.revokeSession(p.sessionId(),clock.millis()); }

    public List<DeviceView> devices(String rawAccessToken){ Principal p=authenticateBearer(rawAccessToken); return views(repository.sessions(p.userId())); }
    public List<DeviceView> devicesForDiscord(long discordUserId){
        BaskovUser user=repository.findUserByIdentity(IdentityProvider.DISCORD,Long.toUnsignedString(discordUserId)).orElse(null);
        return user==null?List.of():views(repository.sessions(user.userId()));
    }

    public synchronized void revokeDevice(String rawAccessToken,String sessionId){
        Principal p=authenticateBearer(rawAccessToken); revokeOwned(p.userId(),sessionId);
    }
    public synchronized void revokeDeviceForDiscord(long discordUserId,String sessionId){
        BaskovUser user=repository.findUserByIdentity(IdentityProvider.DISCORD,Long.toUnsignedString(discordUserId)).orElseThrow(()->new AuthException("USER_NOT_PAIRED","No Baskov user is linked to this Discord account"));
        revokeOwned(user.userId(),sessionId);
    }

    private void revokeOwned(String userId,String sessionId){ DeviceSession s=repository.findSession(required(sessionId,"sessionId")).orElseThrow(()->new AuthException("DEVICE_NOT_FOUND","Device session not found")); if(!s.userId().equals(userId)) throw new AuthException("DEVICE_NOT_FOUND","Device session not found"); repository.revokeSession(s.sessionId(),clock.millis()); }

    private BaskovUser createDiscordUser(PairingCodeStore.PairingGrant grant){
        long now=clock.millis(); BaskovUser u=new BaskovUser(UUID.randomUUID().toString(),Math.max(1L,now),grant.displayName()); repository.saveUser(u); repository.linkIdentity(ExternalIdentity.discord(u.userId(),grant.discordUserId())); return u;
    }
    private TokenPair createSession(BaskovUser user,String deviceName){ long now=clock.millis(); String access=token("bka_",32),refresh=token("bkr_",48); DeviceSession s=new DeviceSession(UUID.randomUUID().toString(),user.userId(),deviceName,hash(access),hash(refresh),now+properties.getAccessTokenTtl().toMillis(),now+properties.getRefreshTokenTtl().toMillis(),Math.max(1L,now),Math.max(1L,now),0L); repository.saveSession(s); return response(principal(s),access,refresh,s); }
    private Principal principal(DeviceSession s){ BaskovUser u=repository.findUser(s.userId()).orElseThrow(()->new AuthException("USER_NOT_FOUND","Baskov user not found")); ExternalIdentity discord=repository.findIdentity(u.userId(),IdentityProvider.DISCORD).orElseThrow(()->new AuthException("DISCORD_IDENTITY_MISSING","Discord identity is not linked")); return new Principal(u.userId(),u.displayName(),discord.discordUserId(),s.sessionId(),s.deviceName()); }
    private TokenPair response(Principal p,String access,String refresh,DeviceSession s){ return new TokenPair(p,access,refresh,s.accessExpiresAtEpochMillis(),s.refreshExpiresAtEpochMillis()); }
    private List<DeviceView> views(List<DeviceSession> sessions){ long now=clock.millis(); return sessions.stream().map(s->new DeviceView(s.sessionId(),s.deviceName(),s.createdAtEpochMillis(),s.lastRefreshedAtEpochMillis(),s.revoked(),s.refreshExpired(now))).toList(); }
    private String token(String prefix,int bytes){ byte[] raw=new byte[bytes]; random.nextBytes(raw); return prefix+Base64.getUrlEncoder().withoutPadding().encodeToString(raw); }
    static String hash(String token){ try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 unavailable",ex);} }
    private static String normalizeDeviceName(String value){ String v=required(value,"deviceName").trim(); if(v.length()>80) throw new AuthException("DEVICE_NAME_INVALID","Device name is too long"); return v; }
    private static String required(String v,String name){ if(v==null||v.isBlank()) throw new AuthException("AUTH_INPUT_INVALID",name+" cannot be blank"); return v.trim(); }

    public record Principal(String userId,String displayName,long discordUserId,String sessionId,String deviceName) {}
    public record TokenPair(Principal principal,String accessToken,String refreshToken,long accessExpiresAtEpochMillis,long refreshExpiresAtEpochMillis) {}
    public record DeviceView(String sessionId,String deviceName,long createdAtEpochMillis,long lastRefreshedAtEpochMillis,boolean revoked,boolean expired) {}
    public static final class AuthException extends RuntimeException { private final String code; public AuthException(String code,String message){super(message);this.code=code;} public String code(){return code;} }
}
