package at.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class EnergyGeneratorComponent implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<EnergyGeneratorComponent> CODEC =
            BuilderCodec.builder(EnergyGeneratorComponent.class, EnergyGeneratorComponent::new)
                    .append(new KeyedCodec<>("GeneratorType", Codec.STRING),
                            (c, v) -> c.generatorType = GeneratorType.valueOf(v),
                            (c) -> c.generatorType.name())
                    .add()
                    .append(new KeyedCodec<>("GenerationRate", Codec.LONG),
                            (c, v) -> c.baseRate = v,
                            (c) -> c.baseRate)
                    .add()
                    .build();

    private GeneratorType generatorType;
    private long baseRate;

    public EnergyGeneratorComponent() { this(GeneratorType.SOLAR, 10L); }
    public EnergyGeneratorComponent(GeneratorType kind, long baseRate) {
        this.generatorType = kind;
        this.baseRate = baseRate;
    }

    public GeneratorType getGeneratorType() { return generatorType; }
    public long getBaseRate() { return baseRate; }

    @Override
    public Component<ChunkStore> clone() {
        return new EnergyGeneratorComponent(generatorType, baseRate);
    }
}

