package ru.flawden.BascovDiscordBot.auth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.flawden.BascovDiscordBot.config.AuthProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic TSV storage for Baskov users, linked identities and hashed device sessions. */
@Slf4j
@Repository
public class FileAuthRepository implements AuthRepository {
    private static final String HEADER = "BASKOV_AUTH_V1";
    private static final int MAX_SESSION_RECORDS_PER_USER = 32;
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path file;
    private final Object lock = new Object();
    private final Map<String, BaskovUser> users = new LinkedHashMap<>();
    private final Map<String, ExternalIdentity> identities = new LinkedHashMap<>();
    private final Map<String, DeviceSession> sessions = new LinkedHashMap<>();

    public FileAuthRepository(AuthProperties properties) {
        this.file = properties.getFile().toAbsolutePath().normalize();
    }

    @PostConstruct
    public void load() {
        synchronized (lock) {
            users.clear(); identities.clear(); sessions.clear();
            if (Files.notExists(file)) {
                log.info("Baskov auth storage will be created on first device pairing: {}", file);
                return;
            }
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (lines.isEmpty()) return;
                if (!HEADER.equals(lines.get(0))) throw new IllegalStateException("Unsupported Baskov auth format in " + file);
                for (int i=1;i<lines.size();i++) {
                    String line=lines.get(i);
                    if (line.isBlank() || line.startsWith("#")) continue;
                    try { loadLine(line); } catch (RuntimeException ex) { log.warn("Ignoring malformed auth line {}: {}", i+1, ex.getMessage()); }
                }
                log.info("Loaded Baskov auth: users={}, identities={}, deviceSessions={}", users.size(), identities.size(), sessions.size());
            } catch (IOException ex) { throw new IllegalStateException("Cannot read Baskov auth from " + file, ex); }
        }
    }

    @Override public Optional<BaskovUser> findUser(String userId) { synchronized(lock){ return Optional.ofNullable(users.get(userId)); } }
    @Override public Optional<BaskovUser> findUserByIdentity(IdentityProvider provider, String subject) {
        synchronized(lock){ ExternalIdentity id=identities.get(identityKey(provider,subject)); return id==null?Optional.empty():Optional.ofNullable(users.get(id.userId())); }
    }
    @Override public BaskovUser saveUser(BaskovUser user) { synchronized(lock){ users.put(user.userId(),user); persist(); return user; } }
    @Override public ExternalIdentity linkIdentity(ExternalIdentity identity) {
        synchronized(lock){
            if(!users.containsKey(identity.userId())) throw new IllegalArgumentException("Unknown Baskov user");
            String key=identityKey(identity.provider(),identity.subject());
            ExternalIdentity existing=identities.get(key);
            if(existing!=null && !existing.userId().equals(identity.userId())) throw new IllegalStateException("External identity is already linked");
            identities.put(key,identity); persist(); return identity;
        }
    }
    @Override public Optional<ExternalIdentity> findIdentity(String userId, IdentityProvider provider) {
        synchronized(lock){ return identities.values().stream().filter(i->i.userId().equals(userId)&&i.provider()==provider).findFirst(); }
    }
    @Override public DeviceSession saveSession(DeviceSession session) { synchronized(lock){ sessions.put(session.sessionId(),session); trimSessions(session.userId()); persist(); return session; } }
    @Override public Optional<DeviceSession> findSessionByAccessHash(String hash) { synchronized(lock){ return sessions.values().stream().filter(s->s.accessTokenHash().equals(hash)).findFirst(); } }
    @Override public Optional<DeviceSession> findSessionByRefreshHash(String hash) { synchronized(lock){ return sessions.values().stream().filter(s->s.refreshTokenHash().equals(hash)).findFirst(); } }
    @Override public Optional<DeviceSession> findSession(String sessionId) { synchronized(lock){ return Optional.ofNullable(sessions.get(sessionId)); } }
    @Override public List<DeviceSession> sessions(String userId) { synchronized(lock){ return sessions.values().stream().filter(s->s.userId().equals(userId)).sorted(Comparator.comparingLong(DeviceSession::createdAtEpochMillis).reversed()).toList(); } }
    @Override public DeviceSession revokeSession(String sessionId, long now) {
        synchronized(lock){ DeviceSession current=sessions.get(sessionId); if(current==null) throw new IllegalArgumentException("Unknown device session"); DeviceSession revoked=current.revoke(now); sessions.put(sessionId,revoked); persist(); return revoked; }
    }


    private void trimSessions(String userId) {
        List<DeviceSession> owned = sessions.values().stream()
                .filter(session -> session.userId().equals(userId))
                .sorted(Comparator.comparingLong(DeviceSession::createdAtEpochMillis).reversed())
                .toList();
        for (int index = MAX_SESSION_RECORDS_PER_USER; index < owned.size(); index++) {
            DeviceSession candidate = owned.get(index);
            if (candidate.revoked() || candidate.refreshExpired(System.currentTimeMillis())) {
                sessions.remove(candidate.sessionId());
            }
        }
    }

    private void loadLine(String line) {
        String[] c=line.split("\\t",-1);
        switch(c[0]) {
            case "U" -> { if(c.length!=4) throw new IllegalArgumentException("bad U record"); BaskovUser u=new BaskovUser(c[1],Long.parseLong(c[2]),decode(c[3])); users.put(u.userId(),u); }
            case "I" -> { if(c.length!=4) throw new IllegalArgumentException("bad I record"); ExternalIdentity i=new ExternalIdentity(c[1],IdentityProvider.valueOf(c[2]),decode(c[3])); identities.put(identityKey(i.provider(),i.subject()),i); }
            case "S" -> { if(c.length!=11) throw new IllegalArgumentException("bad S record"); DeviceSession s=new DeviceSession(c[1],c[2],decode(c[3]),c[4],c[5],Long.parseLong(c[6]),Long.parseLong(c[7]),Long.parseLong(c[8]),Long.parseLong(c[9]),Long.parseLong(c[10])); sessions.put(s.sessionId(),s); }
            default -> throw new IllegalArgumentException("unknown auth record");
        }
    }

    private void persist() {
        Path parent=file.getParent(); if(parent==null) throw new IllegalStateException("Auth path has no parent");
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try {
            Files.createDirectories(parent);
            List<String> out=new ArrayList<>(); out.add(HEADER);
            users.values().stream().sorted(Comparator.comparing(BaskovUser::userId)).forEach(u->out.add("U\t"+u.userId()+"\t"+u.createdAtEpochMillis()+"\t"+encode(u.displayName())));
            identities.values().stream().sorted(Comparator.comparing(ExternalIdentity::userId).thenComparing(i->i.provider().name())).forEach(i->out.add("I\t"+i.userId()+"\t"+i.provider().name()+"\t"+encode(i.subject())));
            sessions.values().stream().sorted(Comparator.comparing(DeviceSession::sessionId)).forEach(s->out.add(String.join("\t","S",s.sessionId(),s.userId(),encode(s.deviceName()),s.accessTokenHash(),s.refreshTokenHash(),Long.toString(s.accessExpiresAtEpochMillis()),Long.toString(s.refreshExpiresAtEpochMillis()),Long.toString(s.createdAtEpochMillis()),Long.toString(s.lastRefreshedAtEpochMillis()),Long.toString(s.revokedAtEpochMillis()))));
            Files.write(temp,out,StandardCharsets.UTF_8);
            harden(temp);
            try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex){ Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
            harden(file);
        } catch(IOException ex){ throw new IllegalStateException("Cannot persist Baskov auth to "+file,ex); }
        finally { try{Files.deleteIfExists(temp);}catch(IOException ignored){} }
    }
    private static void harden(Path path) throws IOException { try{Files.setPosixFilePermissions(path,OWNER_ONLY);}catch(UnsupportedOperationException ignored){} }
    private static String identityKey(IdentityProvider p,String subject){ return p.name()+":"+subject.trim(); }
    private static String encode(String value){ return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value){ return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8); }
}
