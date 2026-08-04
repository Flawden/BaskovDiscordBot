package ru.flawden.BascovDiscordBot.operations;

import net.dv8tion.jda.api.JDA;

/**
 * Runtime metadata for the exact JDA artifact loaded by the application.
 */
public final class JdaRuntimeInfo {

    public static final String VOICE_MIGRATION_BASELINE = "5.6.1";

    private JdaRuntimeInfo() {
    }

    public static String version() {
        Package jdaPackage = JDA.class.getPackage();
        String implementationVersion = jdaPackage == null
                ? null
                : jdaPackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "unknown"
                : implementationVersion;
    }
}
