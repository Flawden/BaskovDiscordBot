package ru.flawden.BascovDiscordBot.auth;

import java.util.List;
import java.util.Optional;

/** Persistent identity/session boundary. */
public interface AuthRepository {
    Optional<BaskovUser> findUser(String userId);
    Optional<BaskovUser> findUserByIdentity(IdentityProvider provider, String subject);
    BaskovUser saveUser(BaskovUser user);
    ExternalIdentity linkIdentity(ExternalIdentity identity);
    Optional<ExternalIdentity> findIdentity(String userId, IdentityProvider provider);
    DeviceSession saveSession(DeviceSession session);
    Optional<DeviceSession> findSessionByAccessHash(String accessHash);
    Optional<DeviceSession> findSessionByRefreshHash(String refreshHash);
    Optional<DeviceSession> findSession(String sessionId);
    List<DeviceSession> sessions(String userId);
    DeviceSession revokeSession(String sessionId, long revokedAtEpochMillis);
}
