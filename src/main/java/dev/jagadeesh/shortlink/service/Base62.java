package dev.jagadeesh.shortlink.service;

/** Base62 (0-9, A-Z, a-z) encoding of non-negative longs, left-padded to a fixed width. */
public final class Base62 {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long value, int width) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        char[] out = new char[width];
        for (int i = width - 1; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (value % BASE));
            value /= BASE;
        }
        if (value != 0) {
            throw new IllegalArgumentException("value does not fit in " + width + " base62 digits");
        }
        return new String(out);
    }

    public static long decode(String code) {
        long value = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("not a base62 character: " + c);
            }
            value = Math.multiplyExact(value, BASE) + digit;
        }
        return value;
    }
}
