package ru.flawden.BascovDiscordBot.dave;

import moe.kyokobot.libdave.NativeDaveFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Реальный JNI smoke-test для платформ, на которых релиз поставляет native JAR.
 */
class NativeDaveRuntimeTest {

    @Test
    void nativeLibraryLoadsAndAdvertisesDaveProtocol() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean supported = (os.contains("linux") || os.contains("windows"))
                && (arch.equals("amd64") || arch.equals("x86_64"));
        Assumptions.assumeTrue(supported,
                () -> "No release native artifact configured for " + os + "/" + arch);

        NativeDaveFactory.ensureAvailable();
        int maxProtocolVersion = new NativeDaveFactory().maxSupportedProtocolVersion();

        assertTrue(maxProtocolVersion > 0,
                () -> "Native libDAVE must advertise a positive protocol version, got "
                        + maxProtocolVersion);
    }
}
