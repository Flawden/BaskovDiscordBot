package ru.flawden.BascovDiscordBot.product.api;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.auth.DeviceAuthService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductApiAccessGuardTest {
    @Test void extractsBearerTokenCaseInsensitively() {
        assertEquals("token-123", ProductApiAccessGuard.bearer("bEaReR token-123"));
    }

    @Test void missingAuthorizationIsStableAuthError() {
        var ex = assertThrows(DeviceAuthService.AuthException.class, () -> ProductApiAccessGuard.bearer(null));
        assertEquals("ACCESS_TOKEN_MISSING", ex.code());
    }

    @Test void malformedAuthorizationIsStableAuthError() {
        var ex = assertThrows(DeviceAuthService.AuthException.class, () -> ProductApiAccessGuard.bearer("Basic abc"));
        assertEquals("ACCESS_TOKEN_MISSING", ex.code());
    }

    @Test void emptyBearerIsStableAuthError() {
        var ex = assertThrows(DeviceAuthService.AuthException.class, () -> ProductApiAccessGuard.bearer("Bearer   "));
        assertEquals("ACCESS_TOKEN_MISSING", ex.code());
    }
    @Test
    void discoversGuildsForAuthenticatedLinkedDiscordIdentity() {
        DeviceAuthService auth = mock(DeviceAuthService.class);
        ProductGuildAccessPort guildAccess = mock(ProductGuildAccessPort.class);
        var principal = new DeviceAuthService.Principal("baskov-user-1", "Alex", 42L, "session-1", "Pixel");
        when(auth.authenticateBearer("token-123")).thenReturn(principal);
        when(guildAccess.accessibleGuilds(42L)).thenReturn(List.of(
                new ProductGuildAccessPort.GuildSummary(10L, "Music")));

        var result = new ProductApiAccessGuard(auth, guildAccess).requireGuilds("Bearer token-123");

        assertEquals("baskov-user-1", result.principal().userId());
        assertEquals(1, result.guilds().size());
        assertEquals(10L, result.guilds().get(0).guildId());
    }
}
