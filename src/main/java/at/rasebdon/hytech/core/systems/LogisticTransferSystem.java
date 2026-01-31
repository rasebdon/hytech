package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class LogisticTransferSystem<TContainer> extends TickingSystem<ChunkStore> {
    private final List<LogisticBlockComponent<TContainer>> containerComponents;
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
        if (event.getComponent() instanceof LogisticBlockComponent<TContainer> blockComponent) {
            if (event.isAdded()) {
                addLogisticBlock(blockComponent);
            } else if (event.isRemoved()) {
                removeLogisticBlock(blockComponent);
            }
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

    protected abstract void transfer(LogisticBlockComponent<TContainer> source);

    private void addLogisticBlock(LogisticBlockComponent<TContainer> block) {
        containerComponents.add(block);
        containerComponents.sort(Comparator.comparingInt(LogisticBlockComponent<TContainer>::getTransferPriority));
    }

    private void removeLogisticBlock(LogisticBlockComponent<TContainer> block) {
        containerComponents.remove(block);
    }
}
