package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class LogisticTransferSystem<TContainer> extends TickingSystem<ChunkStore> {
    private final List<LogisticContainerComponent<TContainer>> containerComponents;
    private final List<LogisticNetwork<TContainer>> networks;

    protected LogisticTransferSystem(
            IEventRegistry eventRegistry,
            Class<? extends LogisticContainerChangedEvent<TContainer>> containerChangedEventClass,
            Class<? extends LogisticNetworkChangedEvent<TContainer>> networkChangedEventClass) {
        eventRegistry.register(containerChangedEventClass, this::handleLogisticContainerChanged);
        eventRegistry.register(networkChangedEventClass, this::handleLogisticNetworkChanged);

        this.networks = new ArrayList<>();
        this.containerComponents = new ArrayList<>();
    }

    private void handleLogisticContainerChanged(LogisticContainerChangedEvent<TContainer> event) {
        if (event.isAdded()) {
            addContainerComponent(event.getComponent());
        } else if (event.isRemoved()) {
            removeContainerComponent(event.getComponent());
        }
    }

    private void handleLogisticNetworkChanged(LogisticNetworkChangedEvent<TContainer> event) {
        if (event.isAdded()) {
            networks.add(event.getComponent());
        } else if (event.isRemoved()) {
            networks.remove(event.getComponent());
        }
    }

    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        for (var network : networks) {
            network.pullFromTargets();
        }

        for (var container : containerComponents) {
            transfer(container);
        }

        for (var network : networks) {
            network.pushToTargets();
        }
    }

    protected abstract void transfer(LogisticContainerComponent<TContainer> source);

    private void addContainerComponent(LogisticContainerComponent<TContainer> container) {
        containerComponents.add(container);
        containerComponents.sort(Comparator.comparingInt(LogisticContainerComponent<TContainer>::getTransferPriority));
    }

    private void removeContainerComponent(LogisticContainerComponent<TContainer> container) {
        containerComponents.remove(container);
    }
}
