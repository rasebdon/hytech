package at.rasebdon.hytech.items;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.items.components.ItemBlockComponent;
import at.rasebdon.hytech.items.components.ItemPipeComponent;
import at.rasebdon.hytech.items.networks.ItemNetworkSystem;
import at.rasebdon.hytech.items.systems.ItemBlockStateRegistrationSystem;
import at.rasebdon.hytech.items.systems.ItemComponentRegistrationSystem;
import at.rasebdon.hytech.items.systems.ItemTransferSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class ItemModule extends AbstractLogisticModule<
        ItemBlockComponent,
        ItemPipeComponent,
        ItemComponentRegistrationSystem,
        HytechItemContainer
        > {

    private static ItemModule INSTANCE;

    private ItemModule(
            ComponentRegistryProxy<ChunkStore> registry,
            IEventRegistry eventRegistry
    ) {
        super(
                registry,
                eventRegistry,
                ItemBlockComponent.class,
                "hytech:item:container",
                ItemBlockComponent.CODEC,
                ItemPipeComponent.class,
                "hytech:item:pipe",
                ItemPipeComponent.CODEC
        );
    }

    public static void init(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new ItemModule(registry, eventRegistry);
    }

    public static ItemModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    @Override
    protected void registerAdditionalSystems(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        var itemBlockStateRegistrationSystem = new ItemBlockStateRegistrationSystem(registrationSystem);
        registry.registerSystem(itemBlockStateRegistrationSystem);
    }

    @Override
    protected String getModuleName() {
        return "Item Module";
    }

    @Override
    protected LogisticNetworkSystem<HytechItemContainer> createNetworkSystem() {
        return new ItemNetworkSystem();
    }

    @Override
    protected LogisticTransferSystem<HytechItemContainer> createTransferSystem(IEventRegistry eventRegistry) {
        return new ItemTransferSystem(eventRegistry);
    }

    @Override
    protected ItemComponentRegistrationSystem createContainerRegistrationSystem(
            ComponentType<ChunkStore, ItemBlockComponent> blockType,
            ComponentType<ChunkStore, ItemPipeComponent> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechItemContainer> networkSystem
    ) {
        return new ItemComponentRegistrationSystem(
                blockType,
                pipeType,
                eventRegistry,
                networkSystem
        );
    }
}
