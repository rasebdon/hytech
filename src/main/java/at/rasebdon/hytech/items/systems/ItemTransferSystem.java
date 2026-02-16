package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.items.ItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

public class ItemTransferSystem extends LogisticTransferSystem<ItemContainer> {
    public ItemTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, ItemContainerChangedEvent.class, ItemNetworkChangedEvent.class);
    }

    @Override
    protected void transfer(LogisticBlockComponent<ItemContainer> source) {

    }
}
