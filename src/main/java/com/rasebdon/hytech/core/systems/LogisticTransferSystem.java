package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.ILogisticContainer;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class LogisticTransferSystem<TContainer extends ILogisticContainer> extends TickingSystem<ChunkStore> {
    private final List<LogisticContainerComponent<TContainer>> energyContainers;
    private final List<LogisticNetwork<TContainer>> networks;

    protected LogisticTransferSystem(
            IEventRegistry eventRegistry,
            Class<? extends LogisticContainerChangedEvent<TContainer>> eventClass) {
        eventRegistry.register(eventClass, this::handleLogisticContainerChanged);

        // TODO : Add network change event

        this.networks = new ArrayList<>();
        this.energyContainers = new ArrayList<>();
    }

    private void handleLogisticContainerChanged(LogisticContainerChangedEvent<TContainer> event) {
        if (event.isAdded()) {
            addEnergyContainer(event.getComponent());
        } else if (event.isRemoved()) {
            removeEnergyContainer(event.getComponent());
        }
    }

    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        for (var network : networks) {
            network.pullFromTargets();
        }

        for (var container : energyContainers) {
            transfer(container);
        }

        for (var network : networks) {
            network.pushToTargets();
        }
    }

    protected abstract void transfer(LogisticContainerComponent<TContainer> source);

    private void addEnergyContainer(LogisticContainerComponent<TContainer> container) {
        energyContainers.add(container);
        energyContainers.sort(Comparator.comparingInt(LogisticContainerComponent<TContainer>::getTransferPriority));
    }

    private void removeEnergyContainer(LogisticContainerComponent<TContainer> container) {
        energyContainers.remove(container);
    }
}
