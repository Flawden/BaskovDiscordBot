package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AuthDeviceSessionsContractTest {
    @Test void authStorageNeverPersistsPlaintextTokens() throws Exception {String repo=read("auth/FileAuthRepository.java");assertTrue(repo.contains("accessTokenHash"));assertTrue(repo.contains("refreshTokenHash"));assertFalse(repo.contains("accessToken()"));assertFalse(repo.contains("refreshToken()"));}
    @Test void pairingCodesAreOneTimeProcessLocalGrants() throws Exception {String store=read("auth/PairingCodeStore.java");assertTrue(store.contains("grants.remove(code)"));assertTrue(store.contains("getPairingTtl"));assertFalse(store.contains("Files."));}
    @Test void productReadsDeriveIdentityFromBearerInsteadOfUserIdQuery() throws Exception {String c=read("product/api/ProductApiController.java");assertTrue(c.contains("HttpHeaders.AUTHORIZATION"));assertTrue(c.contains("access.requireGuild"));assertFalse(c.contains("@RequestParam long userId"));}
    @Test void wireUsesBaskovUserIdWhileLegacyDiscordIdStaysInsideBackend() throws Exception {String response=read("product/api/ProductApiResponse.java");String authResponse=read("product/api/ProductAuthApiResponse.java");String controller=read("product/api/ProductApiController.java");assertTrue(response.contains("String userId"));assertFalse(authResponse.contains("discordUserId"));assertTrue(controller.contains("principal.userId()"));assertTrue(controller.contains("principal.discordUserId()"));}
    @Test void guildAuthorizationIsBehindPortAndControllerDoesNotImportJda() throws Exception {String c=read("product/api/ProductApiController.java");String guard=read("product/api/ProductApiAccessGuard.java");assertFalse(c.contains("net.dv8tion"));assertTrue(guard.contains("ProductGuildAccessPort"));}
    @Test void authApiSupportsPairRefreshLogoutMeDevicesAndRevoke() throws Exception {String c=read("product/api/ProductAuthApiController.java");assertTrue(c.contains("/device/pair"));assertTrue(c.contains("/refresh"));assertTrue(c.contains("/logout"));assertTrue(c.contains("/me"));assertTrue(c.contains("/devices"));assertTrue(c.contains("@DeleteMapping"));}
    @Test void apiRemainsDisabledLoopbackAndUnpublishedByDefault() throws Exception {String props=Files.readString(Path.of("src/main/resources/application.properties"));String compose=Files.readString(Path.of("deploy/docker-compose.yml"));assertTrue(props.contains("BASKOV_PRODUCT_API_ENABLED:false"));assertTrue(props.contains("BASKOV_PRODUCT_API_BIND_ADDRESS:127.0.0.1"));assertFalse(compose.contains("18080:18080"));}
    @Test void discordOwnsPairingProofInsteadOfTrustingArbitraryDiscordIdFromHttp() throws Exception {String i=read("interactions/ModernInteractions.java");String auth=read("product/api/ProductAuthApiController.java");assertTrue(i.contains("issueDiscordPairing"));assertFalse(auth.contains("request.discordUserId"));assertFalse(auth.contains("DiscordIdentity"));}
    @Test void devicePairingCodeIsEphemeralToRequestingDiscordUser() throws Exception {String i=read("interactions/ModernInteractions.java");int start=i.indexOf("private void device(");int end=i.indexOf("private void home(", start);String device=i.substring(start,end);assertTrue(device.contains("issueDiscordPairing"));assertTrue(device.contains(".setEphemeral(true)"));}
    @Test void memberDisplayNamesUseJdaEffectiveNameApi() throws Exception {String i=read("interactions/ModernInteractions.java");assertTrue(i.contains("member.getEffectiveName()"));assertFalse(i.contains("member.getName()"));}
    @Test void legacyMusicPersistenceIsNotMigratedInsideAuthRelease() throws Exception {String props=Files.readString(Path.of("src/main/resources/application.properties"));assertTrue(props.contains("music-library.tsv"));assertTrue(props.contains("recommendation-feedback.tsv"));assertTrue(props.contains("baskov-auth.tsv"));}
    private static String read(String rel) throws Exception{return Files.readString(Path.of("src/main/java/ru/flawden/BascovDiscordBot").resolve(rel));}
}
