package ru.flawden.BascovDiscordBot.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.config.AuthProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileAuthRepositoryTest {
    @TempDir Path temp;
    @Test void persistsUsersIdentitiesAndOnlyTokenHashes() throws Exception {
        AuthProperties props=new AuthProperties(); props.setFile(temp.resolve("auth.tsv"));
        FileAuthRepository repo=new FileAuthRepository(props); repo.load();
        BaskovUser user=new BaskovUser("11111111-1111-1111-1111-111111111111",1000L,"Alex");
        repo.saveUser(user); repo.linkIdentity(ExternalIdentity.discord(user.userId(),42L));
        repo.saveSession(new DeviceSession("session-1",user.userId(),"Pixel","accesshash","refreshhash",2000L,3000L,1000L,1000L,0L));
        String raw=Files.readString(props.getFile());
        assertTrue(raw.startsWith("BASKOV_AUTH_V1")); assertTrue(raw.contains("accesshash")); assertFalse(raw.contains("bka_plaintext"));
        FileAuthRepository reloaded=new FileAuthRepository(props); reloaded.load();
        assertEquals(user,reloaded.findUserByIdentity(IdentityProvider.DISCORD,"42").orElseThrow());
        assertEquals("Pixel",reloaded.findSession("session-1").orElseThrow().deviceName());
    }
    @Test void revokePersistsAcrossReload() {
        AuthProperties props=new AuthProperties(); props.setFile(temp.resolve("auth.tsv")); FileAuthRepository repo=new FileAuthRepository(props); repo.load();
        BaskovUser u=new BaskovUser("u",1000L,""); repo.saveUser(u); repo.linkIdentity(ExternalIdentity.discord("u",42L)); repo.saveSession(new DeviceSession("s","u","Phone","a","r",2000,3000,1000,1000,0));
        repo.revokeSession("s",1500L);
        FileAuthRepository again=new FileAuthRepository(props); again.load(); assertTrue(again.findSession("s").orElseThrow().revoked());
    }
}
