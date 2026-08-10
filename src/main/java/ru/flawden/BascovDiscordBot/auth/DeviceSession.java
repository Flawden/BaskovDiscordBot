package ru.flawden.BascovDiscordBot.auth;

/** Persistent device session. Only token hashes are stored; plaintext tokens are returned once. */
public record DeviceSession(
        String sessionId,
        String userId,
        String deviceName,
        String accessTokenHash,
        String refreshTokenHash,
        long accessExpiresAtEpochMillis,
        long refreshExpiresAtEpochMillis,
        long createdAtEpochMillis,
        long lastRefreshedAtEpochMillis,
        long revokedAtEpochMillis) {

    public DeviceSession {
        sessionId = required(sessionId, "sessionId");
        userId = required(userId, "userId");
        deviceName = required(deviceName, "deviceName");
        accessTokenHash = required(accessTokenHash, "accessTokenHash");
        refreshTokenHash = required(refreshTokenHash, "refreshTokenHash");
        if (accessExpiresAtEpochMillis <= 0L || refreshExpiresAtEpochMillis <= 0L || createdAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("session timestamps must be positive");
        }
    }

    public boolean revoked() { return revokedAtEpochMillis > 0L; }
    public boolean accessExpired(long now) { return now >= accessExpiresAtEpochMillis; }
    public boolean refreshExpired(long now) { return now >= refreshExpiresAtEpochMillis; }

    public DeviceSession rotate(String newAccessHash, String newRefreshHash, long accessExpiresAt, long refreshExpiresAt, long now) {
        return new DeviceSession(sessionId, userId, deviceName, newAccessHash, newRefreshHash,
                accessExpiresAt, refreshExpiresAt, createdAtEpochMillis, now, revokedAtEpochMillis);
    }

    public DeviceSession revoke(long now) {
        return new DeviceSession(sessionId, userId, deviceName, accessTokenHash, refreshTokenHash,
                accessExpiresAtEpochMillis, refreshExpiresAtEpochMillis, createdAtEpochMillis,
                lastRefreshedAtEpochMillis, Math.max(1L, now));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value.trim();
    }
}
