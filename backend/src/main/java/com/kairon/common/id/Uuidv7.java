package com.kairon.common.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates UUIDv7 values — a 48-bit Unix-millisecond timestamp followed by 74
 * random bits — so primary keys sort in creation order (docs/DATA_MODEL.md).
 * The JDK has no built-in UUIDv7 as of Java 25.
 */
public final class Uuidv7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuidv7() {
    }

    public static UUID next() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        long timestamp = System.currentTimeMillis();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;

        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70); // version 7
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // IETF variant

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
