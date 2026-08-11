package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.auth.DeviceAuthService;

import java.util.List;
import java.util.Objects;

/** Authenticates bearer sessions and enforces Discord-guild membership without exposing JDA to controllers. */
@Component
public class ProductApiAccessGuard {
    private final DeviceAuthService auth;
    private final ProductGuildAccessPort guildAccess;

    public ProductApiAccessGuard(DeviceAuthService auth, ProductGuildAccessPort guildAccess) {
        this.auth=Objects.requireNonNull(auth,"auth"); this.guildAccess=Objects.requireNonNull(guildAccess,"guildAccess");
    }
    public DeviceAuthService.Principal require(String authorization){ return auth.authenticateBearer(bearer(authorization)); }
    public DeviceAuthService.Principal requireGuild(String authorization,long guildId){
        if(guildId<=0L) throw new IllegalArgumentException("guildId must be positive");
        DeviceAuthService.Principal p=require(authorization);
        if(!guildAccess.canAccess(guildId,p.discordUserId())) throw new DeviceAuthService.AuthException("GUILD_ACCESS_DENIED","Linked Discord user is not a member of this guild");
        return p;
    }
    public GuildAccess requireGuilds(String authorization){
        DeviceAuthService.Principal p=require(authorization);
        return new GuildAccess(p, guildAccess.accessibleGuilds(p.discordUserId()));
    }
    public record GuildAccess(DeviceAuthService.Principal principal, List<ProductGuildAccessPort.GuildSummary> guilds) {
        public GuildAccess { guilds = List.copyOf(guilds == null ? List.of() : guilds); }
    }
    static String bearer(String header){
        if(header==null||header.isBlank()) throw new DeviceAuthService.AuthException("ACCESS_TOKEN_MISSING","Authorization Bearer token is required");
        String prefix="Bearer "; if(!header.regionMatches(true,0,prefix,0,prefix.length())) throw new DeviceAuthService.AuthException("ACCESS_TOKEN_MISSING","Authorization Bearer token is required");
        String token=header.substring(prefix.length()).trim(); if(token.isBlank()) throw new DeviceAuthService.AuthException("ACCESS_TOKEN_MISSING","Authorization Bearer token is required"); return token;
    }
}
