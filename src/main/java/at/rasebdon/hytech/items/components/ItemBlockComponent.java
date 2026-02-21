package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.Nullable;

public class ItemBlockComponent extends LogisticBlockComponent<HytechItemContainer> implements HytechItemContainer {
    public static final BuilderCodec<ItemBlockComponent> CODEC =
            BuilderCodec.builder(ItemBlockComponent.class, ItemBlockComponent::new, LogisticBlockComponent.CODEC)
                    .append(new KeyedCodec<>("ItemContainer", ItemContainer.CODEC),
                            (state, o) -> state.itemContainer = o,
                            (state) -> state.itemContainer).add()
                    .build();

    private ItemContainer itemContainer;

    public ItemBlockComponent() {
        this(new BlockFaceConfig(), 0, false);
    }

    public ItemBlockComponent(BlockFaceConfig blockFaceConfig, int transferPriority, boolean isExtracting) {
        super(blockFaceConfig, transferPriority, isExtracting);

        itemContainer = SimpleItemContainer.getNewContainer((short) 16);
    }

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return null;
    }

    @Override
    protected LogisticComponentChangedEvent<HytechItemContainer> createContainerChangedEvent(LogisticChangeType type, LogisticComponent<HytechItemContainer> component) {
        return null;
    }

    @Override
    public HytechItemContainer getContainer() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ItemContainer getItemContainer() {
        return itemContainer;
    }
}
