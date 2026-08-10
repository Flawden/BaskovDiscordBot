package ru.flawden.BascovDiscordBot.auth;

import java.util.Objects;

/** Link between a Baskov user and an identity controlled by an external client/provider. */
public record ExternalIdentity(String userId, IdentityProvider provider, String subject) {
    public ExternalIdentity {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        provider = Objects.requireNonNull(provider, "provider");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject cannot be blank");
        userId = userId.trim();
        subject = subject.trim();
    }

    public static ExternalIdentity discord(String userId, long discordUserId) {
        if (discordUserId <= 0L) throw new IllegalArgumentException("discordUserId must be positive");
        return new ExternalIdentity(userId, IdentityProvider.DISCORD, Long.toUnsignedString(discordUserId));
    }

    public long discordUserId() {
        if (provider != IdentityProvider.DISCORD) throw new IllegalStateException("identity is not Discord");
        return Long.parseUnsignedLong(subject);
    }
}
