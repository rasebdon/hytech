package at.rasebdon.hytech.core.transport;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.BlockFace;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlockFaceConfig implements Cloneable {

    private static final int BLOCK_SIDES = BlockFace.VALUES.length;
    public static final BuilderCodec<BlockFaceConfig> CODEC;
    private static final int TYPE_BITS = 2;
    private static final int INDEX_BITS = 2;
    private static final int BITS_PER_SIDE = TYPE_BITS + INDEX_BITS;
    private static final long SIDE_MASK = (1L << BITS_PER_SIDE) - 1;
    private static final int INDEX_MASK = (1 << INDEX_BITS) - 1;
    private static final BlockFaceConfigType[] PRIORITY = {
            BlockFaceConfigType.BOTH, BlockFaceConfigType.INPUT,
            BlockFaceConfigType.OUTPUT, BlockFaceConfigType.NONE
    };
    private static final long DEFAULT_MASK;
    private static final int INITIAL_PACKED;

    static {
        // Pre-calculate allowed mask and default initial state (BOTH_0)
        long mask = 0L;
        for (BlockFaceConfigType type : PRIORITY) mask |= (1L << combine(type, 0));
        DEFAULT_MASK = mask;
        INITIAL_PACKED = combine(PRIORITY[0], 0);

        // Codec Definition
        var builder = BuilderCodec.builder(BlockFaceConfig.class, BlockFaceConfig::new);
        for (BlockFace face : BlockFace.VALUES) {
            builder.append(new KeyedCodec<>(face.name(), Codec.STRING_ARRAY),
                    (c, v) -> c.setAllowed(face, v), (c) -> c.getAllowed(face)).add();
        }
        builder.append(new KeyedCodec<>("BlockFaceConfigBitmap", Codec.LONG),
                (c, v) -> c.currentBitmap = v, (c) -> c.currentBitmap).add();
        CODEC = builder.build();
    }

    private final long[] allowedMasks = new long[BLOCK_SIDES];
    private long currentBitmap;

    public BlockFaceConfig() {
        Arrays.fill(allowedMasks, DEFAULT_MASK);
        for (BlockFace face : BlockFace.VALUES) {
            insert(face, INITIAL_PACKED);
        }
    }

    private static int combine(BlockFaceConfigType type, int index) {
        return (type.getBits() << INDEX_BITS) | (index & INDEX_MASK);
    }

    private int getOffset(BlockFace face) {
        return face.getValue() * BITS_PER_SIDE;
    }

    private void insert(BlockFace face, int packed) {
        int shift = getOffset(face);
        currentBitmap = (currentBitmap & ~(SIDE_MASK << shift)) | ((long) packed << shift);
    }

    private int extract(BlockFace face) {
        return (int) ((currentBitmap >> getOffset(face)) & SIDE_MASK);
    }

    public void set(BlockFace face, BlockFaceConfigType type, int index) {
        if (!isAllowed(face, type, index)) {
            BlockFaceConfigState fallback = getFallback(face);
            type = fallback.type();
            index = fallback.index();
        }
        insert(face, combine(type, index));
    }

    public BlockFaceConfigType getType(BlockFace face) {
        return BlockFaceConfigType.fromBits(extract(face) >> INDEX_BITS);
    }

    public int getIndex(BlockFace face) {
        return extract(face) & INDEX_MASK;
    }

    public BlockFaceConfigState getState(BlockFace face) {
        return new BlockFaceConfigState(getType(face), getIndex(face));
    }

    public boolean isInput(BlockFace face) {
        return getType(face).isInput();
    }

    public boolean isOutput(BlockFace face) {
        return getType(face).isOutput();
    }

    public boolean isAllowed(BlockFace face, BlockFaceConfigType type, int index) {
        return (allowedMasks[face.getValue()] & (1L << combine(type, index))) != 0;
    }

    public void setAllowed(BlockFace face, @Nonnull String[] values) {
        long mask = 0L;
        for (String val : values) {
            String[] p = val.split("_");
            int idx = (p.length > 1) ? Integer.parseInt(p[1]) : 0;
            mask |= (1L << combine(BlockFaceConfigType.valueOf(p[0]), idx));
        }
        allowedMasks[face.getValue()] = (mask == 0) ? DEFAULT_MASK : mask;
        sanitize(face);
    }

    public String[] getAllowed(BlockFace face) {
        long mask = allowedMasks[face.getValue()];
        List<String> res = new ArrayList<>();
        for (BlockFaceConfigType t : BlockFaceConfigType.values()) {
            for (int i = 0; i <= INDEX_MASK; i++) {
                if ((mask & (1L << combine(t, i))) != 0) {
                    res.add(i == 0 ? t.name() : t.name() + "_" + i);
                }
            }
        }
        return res.toArray(String[]::new);
    }

    public BlockFaceConfigState getFallback(BlockFace face) {
        long mask = allowedMasks[face.getValue()];
        for (BlockFaceConfigType t : PRIORITY) {
            for (int i = 0; i <= INDEX_MASK; i++) {
                if ((mask & (1L << combine(t, i))) != 0) return new BlockFaceConfigState(t, i);
            }
        }
        return new BlockFaceConfigState(BlockFaceConfigType.NONE, 0);
    }

    public void sanitize(BlockFace face) {
        if (!isAllowed(face, getType(face), getIndex(face))) {
            BlockFaceConfigState fb = getFallback(face);
            set(face, fb.type(), fb.index());
        }
    }

    public void cycleFace(BlockFace face) {
        long mask = allowedMasks[face.getValue()];
        int current = extract(face);
        for (int i = 1; i < (1 << BITS_PER_SIDE); i++) {
            int next = (current + i) % (1 << BITS_PER_SIDE);
            if ((mask & (1L << next)) != 0) {
                insert(face, next);
                return;
            }
        }
    }

    public BlockFaceConfigState[] getCurrentStates() {
        var res = new BlockFaceConfigState[BLOCK_SIDES];
        for (int i = 0; i < BLOCK_SIDES; i++) res[i] = getState(BlockFace.fromValue(i));
        return res;
    }

    @Override
    public BlockFaceConfig clone() {
        try {
            BlockFaceConfig clone = (BlockFaceConfig) super.clone();
            System.arraycopy(this.allowedMasks, 0, clone.allowedMasks, 0, BLOCK_SIDES);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}