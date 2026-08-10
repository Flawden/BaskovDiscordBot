package ru.flawden.BascovDiscordBot.product.api;

import java.util.List;

/** Auth/device wire DTOs. Plaintext tokens only exist in pair/refresh responses. */
public final class ProductAuthApiResponse {
    private ProductAuthApiResponse() {}
    public record PairRequest(String code,String deviceName) {}
    public record RefreshRequest(String refreshToken) {}
    public record Tokens(String userId,String sessionId,String deviceName,String accessToken,String refreshToken,long accessExpiresAtEpochMillis,long refreshExpiresAtEpochMillis) {}
    public record Me(String userId,String displayName,String sessionId,String deviceName) {}
    public record Device(String sessionId,String deviceName,long createdAtEpochMillis,long lastRefreshedAtEpochMillis,boolean revoked,boolean expired) {}
    public record Devices(List<Device> devices) { public Devices { devices=List.copyOf(devices==null?List.of():devices); } }
    public record Status(String status) {}
}
