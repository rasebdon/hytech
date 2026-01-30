package com.rasebdon.hytech.core.transport;

import com.hypixel.hytale.protocol.BlockFace;

public class BlockFaceConfig implements Cloneable {
    private static final int DEFAULT_BLOCK_FACE_CONFIG_BITMAP = 0b11_11_11_11_11_11_11;

    private static final int BLOCK_SIDES = 7;
    private static final int BITS_PER_SIDE = 2;
    private static final int SIDE_MASK = 0b11;

    private int blockFaceConfigBitmap;

    public BlockFaceConfig() {
        this(DEFAULT_BLOCK_FACE_CONFIG_BITMAP);
    }

    public BlockFaceConfig(int sideConfigBitmap) {
        this.blockFaceConfigBitmap = sideConfigBitmap;
    }

    private static int getFaceBitShift(BlockFace face) {
        return face.getValue() * BITS_PER_SIDE;
    }

    @Override
    public BlockFaceConfig clone() {
        final BlockFaceConfig clone;
        try {
            clone = (BlockFaceConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
        clone.blockFaceConfigBitmap = this.blockFaceConfigBitmap;
        return clone;
    }

    public BlockFaceConfigType[] toArray() {
        BlockFaceConfigType[] result = new BlockFaceConfigType[BLOCK_SIDES];

        for (int i = 0; i < BLOCK_SIDES; i++) {
            int bits = (blockFaceConfigBitmap >> (i * BITS_PER_SIDE)) & SIDE_MASK;
            result[i] = BlockFaceConfigType.fromBits(bits);
        }

        return result;
    }

    public BlockFaceConfigType getFaceConfigType(BlockFace face) {
        int shift = getFaceBitShift(face);
        return BlockFaceConfigType.fromBits((blockFaceConfigBitmap >> shift) & SIDE_MASK);
    }

    public void setFaceConfigType(BlockFace face, BlockFaceConfigType config) {
        int shift = getFaceBitShift(face);
        blockFaceConfigBitmap =
                (blockFaceConfigBitmap & ~(SIDE_MASK << shift))
                        | (config.getBits() << shift);
    }

    public boolean isOutput(BlockFace face) {
        return this.getFaceConfigType(face).isOutput();
    }

    public boolean isInput(BlockFace face) {
        return this.getFaceConfigType(face).isInput();
    }

    public void cycleFaceConfigType(BlockFace face) {
        setFaceConfigType(face, getFaceConfigType(face).next());
    }

    public int getBitmap() {
        return this.blockFaceConfigBitmap;
    }
}
