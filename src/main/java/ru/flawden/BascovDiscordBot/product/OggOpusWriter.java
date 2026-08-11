package ru.flawden.BascovDiscordBot.product;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Minimal streaming Ogg/Opus muxer for LavaPlayer's Discord Opus packets.
 *
 * <p>The writer deliberately performs no decode/transcode step. Each packet is wrapped
 * into an Ogg page, keeping mobile playback isolated from provider-specific extraction.</p>
 */
public final class OggOpusWriter {

    private static final byte[] CAPTURE = {'O', 'g', 'g', 'S'};
    private static final int OPUS_SAMPLES_PER_20_MS = 960;
    private static final int OGG_CRC_POLYNOMIAL = 0x04C11DB7;
    private static final int[] CRC_TABLE = crcTable();

    private final OutputStream output;
    private final int serial;
    private int sequence;
    private long granulePosition;
    private byte[] pendingPacket;

    public OggOpusWriter(OutputStream output) throws IOException {
        this(output, ThreadLocalRandom.current().nextInt());
    }

    OggOpusWriter(OutputStream output, int serial) throws IOException {
        this.output = Objects.requireNonNull(output, "output");
        this.serial = serial;
        writePage(opusHead(), 0x02, 0L);
        writePage(opusTags(), 0x00, 0L);
        output.flush();
    }

    /**
     * Queues one complete Opus packet. The previous packet is emitted so the final packet
     * can be marked with the Ogg end-of-stream flag when {@link #finish()} is called.
     */
    public void packet(byte[] packet) throws IOException {
        if (packet == null || packet.length == 0) {
            return;
        }
        if (pendingPacket != null) {
            granulePosition += OPUS_SAMPLES_PER_20_MS;
            writePage(pendingPacket, 0x00, granulePosition);
        }
        pendingPacket = Arrays.copyOf(packet, packet.length);
    }

    public void finish() throws IOException {
        if (pendingPacket != null) {
            granulePosition += OPUS_SAMPLES_PER_20_MS;
            writePage(pendingPacket, 0x04, granulePosition);
            pendingPacket = null;
        }
        output.flush();
    }

    private void writePage(byte[] payload, int headerType, long granule) throws IOException {
        int fullSegments = payload.length / 255;
        int remainder = payload.length % 255;
        int segmentCount = fullSegments + 1;
        if (segmentCount > 255) {
            throw new IllegalArgumentException("Opus packet is too large for a single Ogg page");
        }

        byte[] page = new byte[27 + segmentCount + payload.length];
        int offset = 0;
        for (byte value : CAPTURE) page[offset++] = value;
        page[offset++] = 0; // stream structure version
        page[offset++] = (byte) headerType;
        putLongLe(page, offset, granule);
        offset += 8;
        putIntLe(page, offset, serial);
        offset += 4;
        putIntLe(page, offset, sequence++);
        offset += 4;
        offset += 4; // checksum left zero while calculating
        page[offset++] = (byte) segmentCount;

        for (int i = 0; i < fullSegments; i++) {
            page[offset++] = (byte) 255;
        }
        page[offset++] = (byte) remainder;
        System.arraycopy(payload, 0, page, offset, payload.length);

        int checksum = checksum(page);
        putIntLe(page, 22, checksum);
        output.write(page);
    }

    private static byte[] opusHead() {
        byte[] head = new byte[19];
        byte[] magic = "OpusHead".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, head, 0, magic.length);
        head[8] = 1; // version
        head[9] = 2; // stereo
        putShortLe(head, 10, 0); // no additional pre-skip; LavaPlayer has already encoded packets
        putIntLe(head, 12, 48_000);
        putShortLe(head, 16, 0); // output gain
        head[18] = 0; // channel mapping family for mono/stereo
        return head;
    }

    private static byte[] opusTags() {
        byte[] vendor = "Baskov Music".getBytes(StandardCharsets.UTF_8);
        byte[] tags = new byte[8 + 4 + vendor.length + 4];
        byte[] magic = "OpusTags".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, tags, 0, magic.length);
        putIntLe(tags, 8, vendor.length);
        System.arraycopy(vendor, 0, tags, 12, vendor.length);
        putIntLe(tags, 12 + vendor.length, 0); // user comment list length
        return tags;
    }

    private static int checksum(byte[] bytes) {
        int crc = 0;
        for (byte value : bytes) {
            int index = ((crc >>> 24) ^ (value & 0xff)) & 0xff;
            crc = (crc << 8) ^ CRC_TABLE[index];
        }
        return crc;
    }

    private static int[] crcTable() {
        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            int value = i << 24;
            for (int bit = 0; bit < 8; bit++) {
                value = (value & 0x80000000) != 0
                        ? (value << 1) ^ OGG_CRC_POLYNOMIAL
                        : value << 1;
            }
            table[i] = value;
        }
        return table;
    }

    private static void putShortLe(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static void putIntLe(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static void putLongLe(byte[] target, int offset, long value) {
        for (int i = 0; i < 8; i++) {
            target[offset + i] = (byte) (value >>> (8 * i));
        }
    }
}
