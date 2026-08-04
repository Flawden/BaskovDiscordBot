package ru.flawden.BascovDiscordBot.dave;

import lombok.extern.slf4j.Slf4j;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import net.dv8tion.jda.api.audio.AudioModuleConfig;

import java.util.Objects;

/**
 * Загружает JNI libDAVE и формирует JDA audio configuration.
 */
@Slf4j
public final class NativeDaveBootstrap {

    private NativeDaveBootstrap() {
    }

    public static AudioModuleConfig createAudioModuleConfig(DaveRuntimeInfo runtimeInfo) {
        Objects.requireNonNull(runtimeInfo, "runtimeInfo");
        try {
            NativeDaveFactory.ensureAvailable();
            NativeDaveFactory nativeFactory = new NativeDaveFactory();
            int maxProtocolVersion = nativeFactory.maxSupportedProtocolVersion();
            if (maxProtocolVersion <= 0) {
                throw new IllegalStateException(
                        "Native libDAVE reported maximum protocol version " + maxProtocolVersion);
            }

            AudioModuleConfig config = new AudioModuleConfig()
                    .withDaveSessionFactory(new LDJDADaveSessionFactory(nativeFactory));
            runtimeInfo.ready(maxProtocolVersion);
            DaveRuntimeInfo.Snapshot snapshot = runtimeInfo.snapshot();
            log.info(
                    "Native libDAVE ready: implementation={} version={} maxProtocol={} platform={}",
                    snapshot.implementation(),
                    snapshot.implementationVersion(),
                    snapshot.maxProtocolVersion(),
                    snapshot.platform());
            return config;
        } catch (RuntimeException | LinkageError failure) {
            runtimeInfo.failed(failure);
            log.error("Native libDAVE initialization failed", failure);
            throw new IllegalStateException(
                    "Native libDAVE is required for Discord DAVE/E2EE voice transport", failure);
        }
    }
}
