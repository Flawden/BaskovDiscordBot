package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.flawden.BascovDiscordBot.auth.DeviceAuthService;

/** Device pairing and token lifecycle for future Android/Web clients. */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name="baskov.product-api.enabled",havingValue="true")
public class ProductAuthApiController {
    private final DeviceAuthService auth;
    private final ProductApiAccessGuard guard;
    public ProductAuthApiController(DeviceAuthService auth,ProductApiAccessGuard guard){this.auth=auth;this.guard=guard;}

    @PostMapping("/device/pair")
    public ProductAuthApiResponse.Tokens pair(@RequestBody ProductAuthApiResponse.PairRequest request){ return tokens(auth.pairDevice(request.code(),request.deviceName())); }
    @PostMapping("/refresh")
    public ProductAuthApiResponse.Tokens refresh(@RequestBody ProductAuthApiResponse.RefreshRequest request){ return tokens(auth.refresh(request.refreshToken())); }
    @PostMapping("/logout")
    public ProductAuthApiResponse.Status logout(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization){ auth.logout(ProductApiAccessGuard.bearer(authorization)); return new ProductAuthApiResponse.Status("LOGGED_OUT"); }
    @GetMapping("/me")
    public ProductAuthApiResponse.Me me(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization){ return me(guard.require(authorization)); }
    @GetMapping("/devices")
    public ProductAuthApiResponse.Devices devices(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization){ return new ProductAuthApiResponse.Devices(auth.devices(ProductApiAccessGuard.bearer(authorization)).stream().map(ProductAuthApiController::device).toList()); }
    @DeleteMapping("/devices/{sessionId}")
    public ProductAuthApiResponse.Status revoke(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,@PathVariable String sessionId){ auth.revokeDevice(ProductApiAccessGuard.bearer(authorization),sessionId); return new ProductAuthApiResponse.Status("REVOKED"); }

    private static ProductAuthApiResponse.Tokens tokens(DeviceAuthService.TokenPair p){ return new ProductAuthApiResponse.Tokens(p.principal().userId(),p.principal().sessionId(),p.principal().deviceName(),p.accessToken(),p.refreshToken(),p.accessExpiresAtEpochMillis(),p.refreshExpiresAtEpochMillis()); }
    private static ProductAuthApiResponse.Me me(DeviceAuthService.Principal p){ return new ProductAuthApiResponse.Me(p.userId(),p.displayName(),p.sessionId(),p.deviceName()); }
    private static ProductAuthApiResponse.Device device(DeviceAuthService.DeviceView d){ return new ProductAuthApiResponse.Device(d.sessionId(),d.deviceName(),d.createdAtEpochMillis(),d.lastRefreshedAtEpochMillis(),d.revoked(),d.expired()); }
}
