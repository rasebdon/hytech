package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.energy.IEnergyContainer;
import org.jetbrains.annotations.Nullable;

public class EnergyPipeComponent extends LogisticPipeComponent<IEnergyContainer> implements IEnergyContainer {
    public static final BuilderCodec<EnergyPipeComponent> CODEC =
            BuilderCodec.builder(
                            EnergyPipeComponent.class,
                            EnergyPipeComponent::new,
                            LogisticPipeComponent.CODEC)
                    .build();

    @Override
    public IEnergyContainer getContainer() {
        return getNetworkContainer();
    }

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return new EnergyPipeComponent();
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
        assert this.network != null;
        return this.network.getNetworkContainer();
    }
}
