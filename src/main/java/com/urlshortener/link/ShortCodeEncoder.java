package com.urlshortener.link;

/**
 * Encodes a sequence value into a fixed-width, 6-character short code and back.
 *
 * <p>Uses a 36-symbol lowercase alphabet (digits + lowercase letters) rather than a full
 * case-sensitive base62 alphabet: since short codes are stored and compared in lowercase,
 * encoding directly into a lowercase-only alphabet avoids two different sequence values ever
 * producing codes that collide after lowercasing.
 */
public final class ShortCodeEncoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();
    private static final int CODE_LENGTH = 6;
    private static final long MAX_VALUE = pow(BASE, CODE_LENGTH) - 1;

    private ShortCodeEncoder() {
    }

    public static String encode(long sequenceValue) {
        if (sequenceValue < 0) {
            throw new IllegalArgumentException("sequenceValue must not be negative: " + sequenceValue);
        }
        if (sequenceValue > MAX_VALUE) {
            throw new IllegalStateException(
                    "sequenceValue " + sequenceValue + " exceeds " + CODE_LENGTH + "-character capacity ("
                            + MAX_VALUE + ")");
        }

        char[] chars = new char[CODE_LENGTH];
        long remaining = sequenceValue;
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (remaining % BASE));
            remaining /= BASE;
        }
        return new String(chars);
    }

    public static long decode(String shortCode) {
        if (shortCode == null || shortCode.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("shortCode must be exactly " + CODE_LENGTH + " characters");
        }
        long value = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            int digit = ALPHABET.indexOf(shortCode.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("shortCode contains an invalid character: " + shortCode);
            }
            value = value * BASE + digit;
        }
        return value;
    }

    private static long pow(long base, int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }
}
