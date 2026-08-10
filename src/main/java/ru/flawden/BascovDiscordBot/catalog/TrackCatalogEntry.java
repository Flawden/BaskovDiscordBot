package ru.flawden.BascovDiscordBot.catalog;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Client-neutral catalog view of a track. It contains identity and descriptive metadata only.
 */
public record TrackCatalogEntry(
        TrackIdentity identity,
        Set<TrackExternalId> externalIds,
        Set<String> tags) {

    public TrackCatalogEntry {
        identity = Objects.requireNonNull(identity, "identity");
        externalIds = externalIds == null ? Set.of() : Set.copyOf(externalIds);
        tags = normalizeTags(tags);
    }

    public static TrackCatalogEntry of(TrackIdentity identity) {
        return new TrackCatalogEntry(identity, Set.of(), Set.of());
    }

    public TrackCatalogEntry withExternalId(TrackExternalId externalId) {
        if (externalId == null || externalIds.contains(externalId)) {
            return this;
        }
        LinkedHashSet<TrackExternalId> merged = new LinkedHashSet<>(externalIds);
        merged.add(externalId);
        return new TrackCatalogEntry(identity, merged, tags);
    }

    public TrackCatalogEntry withTags(Set<String> newTags) {
        return new TrackCatalogEntry(identity, externalIds, newTags);
    }

    public TrackCatalogEntry merge(TrackCatalogEntry other) {
        Objects.requireNonNull(other, "other");
        if (!identity.sameLogicalTrack(other.identity)) {
            throw new IllegalArgumentException("cannot merge different track identities");
        }
        LinkedHashSet<TrackExternalId> ids = new LinkedHashSet<>(externalIds);
        ids.addAll(other.externalIds);
        LinkedHashSet<String> mergedTags = new LinkedHashSet<>(tags);
        mergedTags.addAll(other.tags);
        return new TrackCatalogEntry(identity, ids, mergedTags);
    }

    private static Set<String> normalizeTags(Set<String> input) {
        if (input == null || input.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String tag : input) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String safe = tag.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (safe.length() > 48) {
                safe = safe.substring(0, 48).trim();
            }
            if (!safe.isBlank()) {
                values.add(safe);
            }
            if (values.size() >= 12) {
                break;
            }
        }
        return Set.copyOf(values);
    }
}
