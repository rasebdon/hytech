package at.rasebdon.hytech.core.util;

public class Validation {
    public static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}
