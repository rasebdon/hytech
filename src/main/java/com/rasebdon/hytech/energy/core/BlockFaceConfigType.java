package com.rasebdon.hytech.energy.core;

public enum BlockFaceConfigType {
    NONE(0b00),
    INPUT(0b01),
    OUTPUT(0b10),
    BOTH(0b11);

    private final int bits;

    BlockFaceConfigType(int bits) {
        this.bits = bits;
    }

    public static BlockFaceConfigType fromBits(int bits) {
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

    public BlockFaceConfigType next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
