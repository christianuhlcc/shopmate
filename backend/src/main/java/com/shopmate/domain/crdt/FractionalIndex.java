package com.shopmate.domain.crdt;

/**
 * Fractional index arithmetic for CRDT sort keys.
 * Generates lexicographically orderable strings for item ordering.
 * Algorithm mirrors frontend fractionalIndex.ts — both must produce identical output.
 *
 * Based on the approach used in Figma/Linear fractional indexing:
 * keys are base-26 strings (a-z) with a digit suffix for disambiguation.
 */
public final class FractionalIndex {

    public static final String MIN = "a0";
    public static final String MAX = "z0";

    private FractionalIndex() {}

    /**
     * Returns a key strictly between {@code before} and {@code after} (lexicographic order).
     * Pass null for before/after to mean "beginning" or "end" of the list.
     */
    public static String between(String before, String after) {
        String lo = before == null ? MIN : before;
        String hi = after == null ? MAX : after;

        if (lo.compareTo(hi) >= 0) {
            throw new IllegalArgumentException(
                "before (" + lo + ") must be less than after (" + hi + ")");
        }

        return midpoint(lo, hi);
    }

    /** Generate a sort key for a new item appended at the end. */
    public static String append(String lastKey) {
        if (lastKey == null) return "a0";
        // Increment the last character
        char last = lastKey.charAt(0);
        if (last < 'z') {
            return String.valueOf((char) (last + 1)) + "0";
        }
        // Overflow: append a suffix
        return lastKey + "m0";
    }

    private static String midpoint(String lo, String hi) {
        // Pad shorter string with 'a' (lowest character) to equal length
        int maxLen = Math.max(lo.length(), hi.length());
        String a = padRight(lo, maxLen, 'a');
        String b = padRight(hi, maxLen, 'a');

        StringBuilder mid = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            int diff = cb - ca;
            if (diff > 1) {
                // Found a gap — take the midpoint character
                mid.append((char) (ca + diff / 2));
                return mid.toString();
            } else if (diff == 1) {
                // Gap of 1 — take the lower character and recurse into suffix
                mid.append(ca);
                // Append midpoint suffix between end-of-lo and end-of-hi
                String loSuffix = lo.length() > i + 1 ? lo.substring(i + 1) : "a";
                String hiSuffix = "z";
                return mid + midpoint(loSuffix, hiSuffix + "z");
            } else {
                // diff == 0: characters are equal, keep going
                mid.append(ca);
            }
        }
        // Strings are equal after padding — append a midpoint suffix
        return mid + midpoint("a", "z");
    }

    private static String padRight(String s, int length, char pad) {
        if (s.length() >= length) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) sb.append(pad);
        return sb.toString();
    }
}
