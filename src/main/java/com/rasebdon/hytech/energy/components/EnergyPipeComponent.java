package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.face.BlockFaceConfig;
import com.rasebdon.hytech.core.util.Validation;
import com.rasebdon.hytech.energy.IEnergyContainer;

import javax.annotation.Nonnull;

public class EnergyPipeComponent extends LogisticPipeComponent<IEnergyContainer> implements IEnergyContainer {

    public static final BuilderCodec<EnergyPipeComponent> CODEC =
            BuilderCodec.builder(EnergyPipeComponent.class, EnergyPipeComponent::new, LogisticPipeComponent.CODEC)
                    .append(new KeyedCodec<>("SavedEnergy", Codec.LONG),
                            (c, v) -> c.savedEnergy = v,
                            (c) -> c.savedEnergy)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Last saved stored energy of pipe")
                    .add()
                    .append(new KeyedCodec<>("PipeCapacity", Codec.LONG),
                            (c, v) -> c.pipeCapacity = v,
                            (c) -> c.pipeCapacity)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy capacity per pipe segment").add()
                    .append(new KeyedCodec<>("PipeTransferSpeed", Codec.LONG),
                            (c, v) -> c.pipeTransferSpeed = v,
                            (c) -> c.pipeTransferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy transferred per tick").add()
                    .build();
    private long savedEnergy;
    private long pipeCapacity;
    private long pipeTransferSpeed;

    public EnergyPipeComponent(
            long savedEnergy,
            long pipeCapacity,
            long pipeTransferSpeed,
            BlockFaceConfig blockFaceConfig
    ) {
        super(blockFaceConfig);

        Validation.requireNonNegative(savedEnergy, "savedEnergy");
        Validation.requireNonNegative(pipeCapacity, "pipeCapacity");
        Validation.requireNonNegative(pipeTransferSpeed, "pipeTransferSpeed");

        this.pipeCapacity = pipeCapacity;
        this.savedEnergy = Math.min(savedEnergy, pipeCapacity);
        this.pipeTransferSpeed = pipeTransferSpeed;
    }

    public EnergyPipeComponent() {
        this(0L, 0L, 0L, new BlockFaceConfig());
    }

    @Override
    public IEnergyContainer getContainer() {
        return getNetworkContainer();
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyPipeComponent(this.savedEnergy, this.pipeCapacity,
                this.pipeTransferSpeed, this.currentBlockFaceConfig.clone());
    }

    @Override
    public long getEnergy() {
        return getNetworkContainer().getEnergy();
    }

    @Override
    public long getTotalCapacity() {
        return getNetworkContainer().getTotalCapacity();
    }

    @Override
    public long getTransferSpeed() {
        return getNetworkContainer().getTransferSpeed();
    }

    @Override
    public void addEnergy(long amount) {
        getNetworkContainer().addEnergy(amount);
    }

    @Override
    public void reduceEnergy(long amount) {
        getNetworkContainer().reduceEnergy(amount);
    }

    private IEnergyContainer getNetworkContainer() {
        if (network == null) {
            throw new IllegalStateException("EnergyPipe has no network");
        }
        return network.getContainer();
    }

    public long getSavedEnergy() {
        return savedEnergy;
    }

    public void setSavedEnergy(long energy) {
        this.savedEnergy = Math.max(0, Math.min(energy, this.pipeCapacity));
    }

    public long getPipeCapacity() {
        return this.pipeCapacity;
    }

    public long getPipeTransferSpeed() {
        return this.pipeTransferSpeed;
    }
}
