package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;


public class ItemTransferSystem extends LogisticTransferSystem<HytechItemContainer> {

    private static final int ITEM_TRANSFER_TICK_RATE = 20;
    private int tickCounter;

    public ItemTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, ItemContainerChangedEvent.class, ItemNetworkChangedEvent.class);
    }

    @Override
    public void tick(float dt, int systemIndex, @NotNull Store<ChunkStore> store) {
        if (!isTransferTick()) return;

        for (var network : this.logisticNetworks) {
            handleNetworkTransfer(network);
        }

        for (var block : this.logisticBlockComponents) {
            handleBlockTransfer(block);
        }
    }

    private void handleBlockTransfer(LogisticBlockComponent<HytechItemContainer> block) {
        // TODO : Special case transfer into network neighbor?
    }

    private void handleNetworkTransfer(LogisticNetwork<HytechItemContainer> network) {

    }

    private boolean isTransferTick() {
        if (tickCounter < ITEM_TRANSFER_TICK_RATE) {
            tickCounter++;
            return false;
        }
        tickCounter = 0;
        return true;
    }
}
