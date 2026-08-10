package ru.flawden.BascovDiscordBot.product.api;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.auth.DeviceAuthService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
