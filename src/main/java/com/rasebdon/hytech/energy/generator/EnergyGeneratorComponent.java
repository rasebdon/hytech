package com.rasebdon.hytech.energy.generator;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;

public class EnergyGeneratorComponent implements Component<ChunkStore> {
    private long generationRate;

    @Nonnull
    public static final BuilderCodec<EnergyGeneratorComponent> CODEC;

    public EnergyGeneratorComponent(long generationRate) {
        this.generationRate = Math.max(0L, generationRate);
    }

    public EnergyGeneratorComponent() {
        this(10L); // Default 10 RF/tick
    }

    public long getGenerationRate() {
        return generationRate;
    }

    public void setGenerationRate(long generationRate) {
        this.generationRate = generationRate;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new EnergyGeneratorComponent(this.generationRate);
    }

    static {
        CODEC = BuilderCodec.builder(EnergyGeneratorComponent.class, EnergyGeneratorComponent::new)
                .append(new KeyedCodec<>("GenerationRate", Codec.LONG),
                        (c, v) -> c.generationRate = v,
                        (c) -> c.generationRate)
                .documentation("Amount of energy generated per tick")
                .add()
                .build();
    }
}
