package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticContainerRegistrationSystem;
import at.rasebdon.hytech.items.ItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ItemContainerRegistrationSystem extends LogisticContainerRegistrationSystem<ItemContainer> {

    public ItemContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<ItemContainer>> blockComponentType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<ItemContainer>> pipeComponentType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<ItemContainer> energyNetworkSystem) {
        super(blockComponentType, pipeComponentType, eventRegistry,
                ItemContainerChangedEvent.class,
                energyNetworkSystem);
    }
}