package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class LogisticTransferSystem<TContainer> extends TickingSystem<ChunkStore> {
    private final List<LogisticContainerComponent<TContainer>> energyContainers;

    protected LogisticTransferSystem() {
        this.energyContainers = new ArrayList<>();
    }


    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        for (var container : energyContainers) {
            transfer(container);
        }
    }

    protected abstract void transfer(LogisticContainerComponent<TContainer> source);

    public void addEnergyContainer(LogisticContainerComponent<TContainer> container) {
        energyContainers.add(container);
        energyContainers.sort(Comparator.comparingInt(LogisticContainerComponent<TContainer>::getTransferPriority));
    }

    public void removeEnergyContainer(LogisticContainerComponent<TContainer> container) {
        energyContainers.remove(container);
    }
}
