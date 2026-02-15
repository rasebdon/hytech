package at.rasebdon.hytech.core.transport;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.BlockFace;

import javax.annotation.Nonnull;

public class BlockFaceConfig implements Cloneable {

    /*
     * Layout:
     * 2 bits per side
     * 7 sides (including NONE)
     * total used bits = 14
     *
     * For each side:
     * 00 = NONE
     * 01 = INPUT
     * 10 = OUTPUT
     * 11 = BOTH
     */

    public static final BuilderCodec<BlockFaceConfig> CODEC;
    private static final int BITS_PER_SIDE = 2;
    private static final int SIDE_MASK = 0b11;
    private static final int BLOCK_SIDES = BlockFace.VALUES.length;
    private static final int DEFAULT_CURRENT_BITMAP = 0b11_11_11_11_11_11_11;

    static {
        var builder = BuilderCodec.builder(BlockFaceConfig.class, BlockFaceConfig::new);

        // Allowed sides
        for (BlockFace face : BlockFace.VALUES) {
            builder.append(
                            new KeyedCodec<>(face.name(), Codec.STRING_ARRAY),
                            (c, v) -> c.setAllowed(face, v),
                            (c) -> c.getAllowed(face)
                    )
                    .documentation(
                            "Allowed face configs for this side.\n" +
                                    "Valid values: NONE, INPUT, OUTPUT, BOTH"
                    )
                    .add();
        }

        // Current bitmap
        builder.append(
                        new KeyedCodec<>("BlockFaceConfigBitmap", Codec.INTEGER),
                        (c, v) -> c.currentBitmap = v,
                        (c) -> c.currentBitmap
                )
                .documentation("Side configuration bitmap")
                .add();

        CODEC = builder.build();
    }

    /* ------------------------------------------------ */
    /* Constructors                                     */
    /* ------------------------------------------------ */

    /**
     * Current configured side modes (2 bits per side)
     */
    private int currentBitmap;
    /**
     * Allowed modes per side (2 bits per side).
     * Each side stores a 2-bit mask representing allowed states.
     * <p>
     * Example:
     * 00 -> only NONE allowed
     * 01 -> only INPUT allowed
     * 10 -> only OUTPUT allowed
     * 11 -> INPUT + OUTPUT allowed (so BOTH is also valid)
     */
    private int allowedBitmap;

    public BlockFaceConfig() {
        this(DEFAULT_CURRENT_BITMAP, DEFAULT_CURRENT_BITMAP);
    }

    /* ------------------------------------------------ */
    /* Core bit logic                                   */
    /* ------------------------------------------------ */

    public BlockFaceConfig(int currentBitmap) {
        this(currentBitmap, DEFAULT_CURRENT_BITMAP);
    }

    public BlockFaceConfig(int currentBitmap, int allowedBitmap) {
        this.currentBitmap = currentBitmap;
        this.allowedBitmap = allowedBitmap;
        sanitizeAll();
    }

    private static int shift(BlockFace face) {
        return face.getValue() * BITS_PER_SIDE;
    }

    /* ------------------------------------------------ */
    /* Current config API                               */
    /* ------------------------------------------------ */

    private static int extract(int bitmap, BlockFace face) {
        return (bitmap >> shift(face)) & SIDE_MASK;
    }

    private static int insert(int bitmap, BlockFace face, int value) {
        int shift = shift(face);
        return (bitmap & ~(SIDE_MASK << shift)) | (value << shift);
    }

    public BlockFaceConfigType get(BlockFace face) {
        return BlockFaceConfigType.fromBits(extract(currentBitmap, face));
    }

    public void set(BlockFace face, BlockFaceConfigType type) {
        if (!isAllowed(face, type)) {
            type = getFallback(face);
        }
        currentBitmap = insert(currentBitmap, face, type.getBits());
    }

    public boolean isInput(BlockFace face) {
        return get(face).isInput();
    }

    /* ------------------------------------------------ */
    /* Allowed config API                               */
    /* ------------------------------------------------ */

    public boolean isOutput(BlockFace face) {
        return get(face).isOutput();
    }

    public BlockFaceConfigType[] getCurrentConfigTypesArray() {
        BlockFaceConfigType[] result = new BlockFaceConfigType[BLOCK_SIDES];
        for (int i = 0; i < BLOCK_SIDES; i++) {
            int bits = (currentBitmap >> (i * BITS_PER_SIDE)) & SIDE_MASK;
            result[i] = BlockFaceConfigType.fromBits(bits);
        }
        return result;
    }

    public boolean isAllowed(BlockFace face, BlockFaceConfigType type) {
        int allowedBits = extract(allowedBitmap, face);
        return (allowedBits & type.getBits()) == type.getBits();
    }

    public void setAllowed(BlockFace face, @Nonnull String[] values) {
        int mask = 0;

        for (String value : values) {
            mask |= BlockFaceConfigType.valueOf(value).getBits();
        }

        if (mask == 0) {
            mask = BlockFaceConfigType.NONE.getBits();
        }

        allowedBitmap = insert(allowedBitmap, face, mask);
        sanitize(face);
    }

    public String[] getAllowed(BlockFace face) {
        int mask = extract(allowedBitmap, face);

        return java.util.Arrays.stream(BlockFaceConfigType.values())
                .filter(t -> (mask & t.getBits()) == t.getBits())
                .map(Enum::name)
                .toArray(String[]::new);
    }

    /* ------------------------------------------------ */
    /* Sanitizing                                       */
    /* ------------------------------------------------ */

    public BlockFaceConfigType nextAllowed(BlockFace face) {
        BlockFaceConfigType[] values = BlockFaceConfigType.values();
        BlockFaceConfigType current = get(face);

        int start = (current.ordinal() + 1) % values.length;

        for (int i = 0; i < values.length; i++) {
            BlockFaceConfigType candidate =
                    values[(start + i) % values.length];

            if (isAllowed(face, candidate)) {
                return candidate;
            }
        }

        return current;
    }

    public BlockFaceConfigType getFallback(BlockFace face) {
        int mask = extract(allowedBitmap, face);

        for (BlockFaceConfigType type : BlockFaceConfigType.values()) {
            if ((mask & type.getBits()) == type.getBits()) {
                return type;
            }
        }

        return BlockFaceConfigType.NONE;
    }

    /* ------------------------------------------------ */
    /* Clone                                            */
    /* ------------------------------------------------ */

    private void sanitize(BlockFace face) {
        if (!isAllowed(face, get(face))) {
            currentBitmap = insert(
                    currentBitmap,
                    face,
                    getFallback(face).getBits()
            );
        }
    }

    /* ------------------------------------------------ */
    /* Codec                                            */
    /* ------------------------------------------------ */

    public void sanitizeAll() {
        for (BlockFace face : BlockFace.VALUES) {
            sanitize(face);
        }
    }

    @Override
    public BlockFaceConfig clone() {
        try {
            return (BlockFaceConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
