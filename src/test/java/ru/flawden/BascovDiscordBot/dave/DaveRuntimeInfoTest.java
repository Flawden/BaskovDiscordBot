package ru.flawden.BascovDiscordBot.dave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaveRuntimeInfoTest {

    @Test
    void startsNotLoadedWithoutExposingSystemDetails() {
        DaveRuntimeInfo info = new DaveRuntimeInfo();

        DaveRuntimeInfo.Snapshot snapshot = info.snapshot();
        assertEquals("NOT_LOADED", snapshot.status());
        assertFalse(snapshot.ready());
        assertEquals(0, snapshot.maxProtocolVersion());
        assertEquals("none", snapshot.error());
    }

    @Test
    void readySnapshotRequiresPositiveProtocolVersion() {
        DaveRuntimeInfo info = new DaveRuntimeInfo();
        info.ready(1);

        DaveRuntimeInfo.Snapshot snapshot = info.snapshot();
        assertTrue(snapshot.ready());
        assertEquals("READY", snapshot.status());
        assertEquals("libdave-jvm", snapshot.implementation());
        assertEquals("ce725965e", snapshot.implementationVersion());
        assertEquals(1, snapshot.maxProtocolVersion());
    }

    @Test
    void failureSnapshotIsSanitized() {
        DaveRuntimeInfo info = new DaveRuntimeInfo();
        info.failed(new IllegalStateException("line one\nline two"));

        DaveRuntimeInfo.Snapshot snapshot = info.snapshot();
        assertEquals("FAILED", snapshot.status());
        assertFalse(snapshot.ready());
        assertFalse(snapshot.error().contains("\n"));
        assertTrue(snapshot.error().contains("IllegalStateException"));
    }
}
