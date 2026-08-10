# Users, Auth & Device Sessions — v1.29.0

## Цель

Discord больше не является единственной системой identity. `BaskovUser` — канонический provider-neutral account, а Discord становится первой `ExternalIdentity`.

```text
BaskovUser
   └── ExternalIdentity(DISCORD, subject)
          └── legacy music profile compatibility

BaskovUser
   └── DeviceSession
          ├── accessTokenHash
          ├── refreshTokenHash
          ├── deviceName
          ├── expirations
          └── revoke state
```

## Pairing proof

HTTP-клиенту запрещено передавать `discordUserId` и объявлять себя владельцем аккаунта. Proof начинается внутри уже аутентифицированного Discord interaction `/device pair`. Код ephemeral, one-time, TTL-bound и исчезает после restart/deploy.

## Device security

- access/refresh tokens генерируются `SecureRandom`;
- storage хранит только SHA-256 hashes;
- refresh делает rotation обоих токенов;
- revoked session больше не аутентифицируется;
- max active device sessions по умолчанию 8;
- Discord `/device status|revoke` позволяет увидеть и отозвать устройства даже если приложение потеряно;
- API guild reads разрешаются только если linked Discord identity состоит в guild.

## Compatibility

`music-library.tsv`, `music-sessions.tsv`, `recommendation-feedback.tsv` и guild settings не мигрируют. В `v1.29` `BaskovUser` является identity/auth boundary, а существующий user-scoped music data lookup делегируется на linked Discord user ID. Это позволяет создать Android account layer без рискованной миграции накопленного recommendation profile в том же релизе.

## Не входит в v1.29

- публичное открытие HTTP порта;
- OAuth/social login кроме Discord pairing proof;
- password auth;
- music control POST endpoints;
- перенос favorites/history/feedback storage keys на UUID BaskovUser;
- Android APK.
