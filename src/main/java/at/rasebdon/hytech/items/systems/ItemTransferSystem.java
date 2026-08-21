package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Item transfer. The algorithm lives in [AbstractTransferSystem]; the item-specific parts
/// -- source slot choice and stack merging -- live in [HytechItemContainer#moveTo], which
/// delegates them to the vanilla container.
public class ItemTransferSystem extends AbstractTransferSystem<HytechItemContainer> {

    public ItemTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, ItemContainerChangedEvent.class, ItemNetworkChangedEvent.class);
    }

    /// Items move once a second rather than every tick, so a pipe run reads as a visible
    /// conveyor rather than teleporting. `MaxTransfer` is therefore per second for items.
    @Override
    protected float getTransferIntervalSeconds() {
        return 1f;
    }
}
