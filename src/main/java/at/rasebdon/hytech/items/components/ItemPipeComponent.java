package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ItemPipeComponent extends LogisticPipeComponent<HytechItemContainer> implements HytechItemContainer {
    public static final BuilderCodec<ItemPipeComponent> CODEC =
            BuilderCodec.builder(ItemPipeComponent.class, ItemPipeComponent::new, LogisticPipeComponent.CODEC)
                    .build();

    public ItemPipeComponent() {
        this(new BlockFaceConfig(), new HashMap<>());
    }

    public ItemPipeComponent(BlockFaceConfig blockFaceConfig, Map<BlockFaceConfigType, String> connectionModelAssetNames) {
        super(blockFaceConfig, connectionModelAssetNames);
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
        return null;
    }
}
