package ru.flawden.BascovDiscordBot.auth;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.AuthProperties;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One-time, process-local pairing grants initiated from Discord. */
@Component
public class PairingCodeStore {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CODES = 256;

    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, PairingGrant> grants = new LinkedHashMap<>();

    public PairingCodeStore(AuthProperties properties) {
        this(properties, Clock.systemUTC(), new SecureRandom());
    }

    PairingCodeStore(AuthProperties properties, Clock clock, SecureRandom random) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized PairingGrant issue(long discordUserId, String displayName) {
        if (discordUserId <= 0L) throw new IllegalArgumentException("discordUserId must be positive");
        purgeExpired();
        grants.entrySet().removeIf(entry -> entry.getValue().discordUserId() == discordUserId);
        while (grants.size() >= MAX_CODES) grants.remove(grants.keySet().iterator().next());
        String code;
        do { code = generateCode(); } while (grants.containsKey(code));
        long now = clock.millis();
        PairingGrant grant = new PairingGrant(code, discordUserId,
                displayName == null ? "" : displayName.trim(), now,
                now + properties.getPairingTtl().toMillis());
        grants.put(code, grant);
        return grant;
    }

    public synchronized Optional<PairingGrant> consume(String rawCode) {
        purgeExpired();
        String code = normalize(rawCode);
        PairingGrant grant = grants.remove(code);
        if (grant == null || grant.expired(clock.millis())) return Optional.empty();
        return Optional.of(grant);
    }

    private void purgeExpired() {
        long now = clock.millis();
        Iterator<Map.Entry<String, PairingGrant>> iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) if (iterator.next().getValue().expired(now)) iterator.remove();
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return builder.toString();
    }

    static String normalize(String code) {
        if (code == null) return "";
        return code.replace("-", "").replace(" ", "").trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record PairingGrant(String code, long discordUserId, String displayName, long issuedAtEpochMillis, long expiresAtEpochMillis) {
        public boolean expired(long now) { return now >= expiresAtEpochMillis; }
    }
}
