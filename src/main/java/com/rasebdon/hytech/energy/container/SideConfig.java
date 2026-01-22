package com.rasebdon.hytech.energy.container;

public enum SideConfig {
    NONE(0b00),
    INPUT(0b01),
    OUTPUT(0b10),
    BOTH(0b11);

    private final int bits;

    SideConfig(int bits) {
        this.bits = bits;
    }

    public static SideConfig fromBits(int bits) {
        return values()[bits & 0b11];
    }

    public int getBits() {
        return bits;
    }

    public boolean canReceive() {
        return (bits & 0b01) != 0;
    }

    public boolean canExtract() {
        return (bits & 0b10) != 0;
    }

    public SideConfig next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
