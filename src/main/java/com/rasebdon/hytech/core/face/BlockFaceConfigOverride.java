package com.rasebdon.hytech.core.face;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.protocol.BlockFace;

public class BlockFaceConfigOverride {
    public static final BuilderCodec<BlockFaceConfigOverride> CODEC;

    static {
        var builder = BuilderCodec.builder(BlockFaceConfigOverride.class, BlockFaceConfigOverride::new);

        for (BlockFace face : BlockFace.values()) {
            createKeyedConfigCodec(builder, face)
                    .documentation("Defines that this face can be configured for all given states " +
                            "(0 = No I/O, 1 = Only Input/None, 2 = Only Output/None, 3 = I/O or None)")
                    .addValidator(Validators.range(0b00, 0b11))
                    .add();
        }

        CODEC = builder.build();
    }

    private final BlockFaceConfig config;

    public BlockFaceConfigOverride() {
        this.config = new BlockFaceConfig();
    }

    private static BuilderField.FieldBuilder<BlockFaceConfigOverride, Integer, BuilderCodec.Builder<BlockFaceConfigOverride>>
    createKeyedConfigCodec(
            BuilderCodec.Builder<BlockFaceConfigOverride> builder,
            BlockFace face
    ) {
        return builder.append(
                new KeyedCodec<>(face.name(), Codec.INTEGER),
                (c, v) -> c.config.setFaceConfigType(face, BlockFaceConfigType.fromBits(v)),
                (c) -> c.config.getFaceConfigType(face).getBits()
        );
    }

    public BlockFaceConfig getConfig() {
        return config;
    }
}
