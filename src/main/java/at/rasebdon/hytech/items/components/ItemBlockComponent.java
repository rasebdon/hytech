package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.items.ItemContainer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.Nullable;

public class ItemBlockComponent extends LogisticBlockComponent<ItemContainer> implements ItemContainer {
    public static final BuilderCodec<ItemBlockComponent> CODEC =
            BuilderCodec.builder(ItemBlockComponent.class, ItemBlockComponent::new, LogisticBlockComponent.CODEC)
                    .build();

    public ItemBlockComponent() {
        this(new BlockFaceConfig(), 0, false);
    }

    public ItemBlockComponent(BlockFaceConfig blockFaceConfig, int transferPriority, boolean isExtracting) {
        super(blockFaceConfig, transferPriority, isExtracting);
    }

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return null;
    }

    @Override
    protected LogisticContainerChangedEvent<ItemContainer> createContainerChangedEvent(LogisticChangeType type, LogisticContainerComponent<ItemContainer> component) {
        return null;
    }

    @Override
    public ItemContainer getContainer() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
