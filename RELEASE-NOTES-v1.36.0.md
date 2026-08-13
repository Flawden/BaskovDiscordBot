# BaskovDiscordBot v1.36.0 — Shared Playlists API

- Exposes the existing guild playlist library to authenticated Baskov clients.
- Supports list/detail/create, add remote track, remove, move, rename and delete.
- Uses the same `MusicLibraryRepository` and `StoredPlaylist` records as Discord `/playlist`, so a playlist created on Android immediately exists for the bot.
- Device pairing does not grant administrator rights: mobile mutation is limited to the linked Discord user's own playlists.
- Track persistence remains provider-backed and server-resolved; Android never stores YouTube/SoundCloud identifiers itself.
- Local `content://` files are intentionally not shareable yet; that requires the future local-track-to-Discord transport.
