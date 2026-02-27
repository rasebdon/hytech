package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.*;

public abstract class LogisticTransferSystem<TContainer> extends TickingSystem<ChunkStore> {
    protected final List<LogisticBlockComponent<TContainer>> logisticBlockComponents;
    protected final Set<LogisticNetwork<TContainer>> logisticNetworks;

    protected LogisticTransferSystem(
            IEventRegistry eventRegistry,
            Class<? extends LogisticComponentChangedEvent<TContainer>> containerChangedEventClass,
            Class<? extends LogisticNetworkChangedEvent<TContainer>> networkChangedEventClass) {
        eventRegistry.register(containerChangedEventClass, this::handleLogisticContainerChanged);
        eventRegistry.register(networkChangedEventClass, this::handleLogisticNetworkChanged);

        this.logisticNetworks = new HashSet<>();
        this.logisticBlockComponents = new ArrayList<>();
    }

    private void handleLogisticContainerChanged(LogisticComponentChangedEvent<TContainer> event) {
        if (event.getComponent() instanceof LogisticBlockComponent<TContainer> blockComponent) {
            if (event.isAdded()) {
                addLogisticBlock(blockComponent);
            } else if (event.isRemoved()) {
                logisticBlockComponents.remove(blockComponent);
            }
        }
    }

    private void handleLogisticNetworkChanged(LogisticNetworkChangedEvent<TContainer> event) {
        if (event.isAdded()) {
            logisticNetworks.add(event.getComponent());
        } else if (event.isRemoved()) {
            logisticNetworks.remove(event.getComponent());
        }
    }

    private void addLogisticBlock(LogisticBlockComponent<TContainer> block) {
        logisticBlockComponents.add(block);
        logisticBlockComponents.sort(Comparator.comparingInt(LogisticBlockComponent<TContainer>::getTransferPriority));
    }
}
