package at.rasebdon.hytech.core.transport;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.BlockFace;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.EnumSet;

public class BlockFaceConfigOverride {
    public static final BuilderCodec<BlockFaceConfigOverride> CODEC;

    static {
        var builder = BuilderCodec.builder(BlockFaceConfigOverride.class, BlockFaceConfigOverride::new);

        for (BlockFace face : BlockFace.values()) {
            builder.append(
                            new KeyedCodec<>(face.name(), Codec.STRING_ARRAY),
                            (c, v) -> c.setAllowed(face, v),
                            (c) -> c.getAllowed(face)
                    )
                    .documentation(
                            "Allowed face configs for this side.\n" +
                                    "Valid values: NONE, INPUT, OUTPUT, INPUT_OUTPUT"
                    )
                    .add();
        }

        CODEC = builder.build();
    }

    private final EnumMap<BlockFace, EnumSet<BlockFaceConfigType>> allowed =
            new EnumMap<>(BlockFace.class);

    public BlockFaceConfigOverride() {
        for (BlockFace face : BlockFace.values()) {
            allowed.put(face, EnumSet.allOf(BlockFaceConfigType.class));
        }
    }

    private void setAllowed(BlockFace face, @Nonnull String[] values) {
        EnumSet<BlockFaceConfigType> set = EnumSet.noneOf(BlockFaceConfigType.class);

        for (String value : values) {
            set.add(BlockFaceConfigType.valueOf(value));
        }

        // Safety: never allow empty
        if (set.isEmpty()) {
            set.add(BlockFaceConfigType.NONE);
        }

        allowed.put(face, set);
    }

    private String[] getAllowed(BlockFace face) {
        return allowed.get(face)
                .stream()
                .map(Enum::name)
                .toArray(String[]::new);
    }

    public boolean isAllowed(BlockFace face, BlockFaceConfigType type) {
        return allowed.get(face).contains(type);
    }

    public BlockFaceConfigType getFallback(BlockFace face) {
        return allowed.get(face).iterator().next();
    }

    public void applyTo(BlockFaceConfig config) {
        for (BlockFace face : BlockFace.values()) {
            BlockFaceConfigType current = config.getFaceConfigType(face);

            if (!isAllowed(face, current)) {
                config.setFaceConfigType(face, getFallback(face));
            }
        }
    }

    public BlockFaceConfigType nextAllowed(
            BlockFace face,
            BlockFaceConfigType current
    ) {
        BlockFaceConfigType[] values = BlockFaceConfigType.values();

        int start = (current.ordinal() + 1) % values.length;

        for (int i = 0; i < values.length; i++) {
            BlockFaceConfigType candidate = values[(start + i) % values.length];

            if (isAllowed(face, candidate)) {
                return candidate;
            }
        }

        return current;
    }
}

