package com.rasebdon.hytech.energy.network;

public enum EnergyCableSideConfig {
    NORMAL(0b00),
    PUSH(0b01),
    PULL(0b10),
    NONE(0b11);

    private final int bits;

    EnergyCableSideConfig(int bits) {
        this.bits = bits;
    }

    public static EnergyCableSideConfig fromBits(int bits) {
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

    public EnergyCableSideConfig next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
