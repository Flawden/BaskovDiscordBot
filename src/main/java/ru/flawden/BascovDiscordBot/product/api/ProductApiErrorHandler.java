package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable v1 validation error shape for the external product adapter. */
@RestControllerAdvice(assignableTypes = ProductApiController.class)
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductApiErrorHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProductApiResponse.Error invalidArgument(IllegalArgumentException exception) {
        return new ProductApiResponse.Error("INVALID_ARGUMENT", exception.getMessage());
    }
}
