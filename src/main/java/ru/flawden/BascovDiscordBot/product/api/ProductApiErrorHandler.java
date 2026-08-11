package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.flawden.BascovDiscordBot.auth.DeviceAuthService;
import ru.flawden.BascovDiscordBot.product.ProductPlaybackUnavailableException;

/** Stable v1 error shape for validation and authentication failures. */
@RestControllerAdvice(basePackages = "ru.flawden.BascovDiscordBot.product.api")
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductApiErrorHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProductApiResponse.Error> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ProductApiResponse.Error("INVALID_ARGUMENT", exception.getMessage()));
    }

    @ExceptionHandler(ProductPlaybackUnavailableException.class)
    public ResponseEntity<ProductApiResponse.Error> playbackUnavailable(ProductPlaybackUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ProductApiResponse.Error("PLAYBACK_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(DeviceAuthService.AuthException.class)
    public ResponseEntity<ProductApiResponse.Error> auth(DeviceAuthService.AuthException exception) {
        HttpStatus status = "GUILD_ACCESS_DENIED".equals(exception.code()) ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        if ("DEVICE_LIMIT".equals(exception.code()) || "DEVICE_NAME_INVALID".equals(exception.code()) || "AUTH_INPUT_INVALID".equals(exception.code())) {
            status = HttpStatus.BAD_REQUEST;
        } else if ("DEVICE_NOT_FOUND".equals(exception.code())) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(new ProductApiResponse.Error(exception.code(), exception.getMessage()));
    }
}
