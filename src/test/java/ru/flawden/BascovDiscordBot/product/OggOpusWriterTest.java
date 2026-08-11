package ru.flawden.BascovDiscordBot.product;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OggOpusWriterTest {

    @Test
    void writesOggOpusHeadersAndMarksFinalAudioPage() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OggOpusWriter writer = new OggOpusWriter(output, 0x12345678);
        writer.packet(new byte[] {1, 2, 3, 4});
        writer.packet(new byte[] {5, 6, 7});
        writer.finish();

        byte[] bytes = output.toByteArray();
        assertTrue(bytes.length > 100);
        assertArrayEquals("OggS".getBytes(StandardCharsets.US_ASCII), slice(bytes, 0, 4));
        assertEquals(0x02, bytes[5] & 0xff); // BOS
        assertTrue(indexOf(bytes, "OpusHead".getBytes(StandardCharsets.US_ASCII)) > 0);
        assertTrue(indexOf(bytes, "OpusTags".getBytes(StandardCharsets.US_ASCII)) > 0);

        int page = 0;
        int lastHeaderType = -1;
        int lastSequence = -1;
        while (page < bytes.length) {
            assertArrayEquals("OggS".getBytes(StandardCharsets.US_ASCII), slice(bytes, page, page + 4));
            lastHeaderType = bytes[page + 5] & 0xff;
            lastSequence = intLe(bytes, page + 18);
            int segments = bytes[page + 26] & 0xff;
            int payload = 0;
            for (int i = 0; i < segments; i++) payload += bytes[page + 27 + i] & 0xff;
            page += 27 + segments + payload;
        }

        assertEquals(bytes.length, page);
        assertEquals(0x04, lastHeaderType); // EOS
        assertEquals(3, lastSequence); // OpusHead, OpusTags, two audio pages => final seq 3
    }

    private static byte[] slice(byte[] source, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }

    private static int indexOf(byte[] source, byte[] needle) {
        outer:
        for (int i = 0; i <= source.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (source[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static int intLe(byte[] source, int offset) {
        return (source[offset] & 0xff)
                | ((source[offset + 1] & 0xff) << 8)
                | ((source[offset + 2] & 0xff) << 16)
                | ((source[offset + 3] & 0xff) << 24);
    }
}
