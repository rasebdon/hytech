package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;

public abstract class LogisticTransferSystem<TContainer> extends TickingSystem<ChunkStore> {
    private final Archetype<ChunkStore> archetype;
    private final ComponentType<ChunkStore, ? extends LogisticContainerComponent<TContainer>> containerComponentType;

    protected LogisticTransferSystem(
            ComponentType<ChunkStore, ? extends LogisticContainerComponent<TContainer>> containerComponentType
    ) {
        this.containerComponentType = containerComponentType;
        this.archetype = Archetype.of(containerComponentType);
    }


    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        store.forEachChunk(this.archetype, (a, _) -> {
            var containers = new ArrayList<LogisticContainerComponent<TContainer>>();

            for (int i = 0; i < a.size(); i++) {
                containers.add(a.getComponent(i, containerComponentType));
            }

            containers.sort(Comparator.comparingInt(LogisticContainerComponent<TContainer>::getTransferPriority));

            for (var container : containers) {
                transfer(container);
            }
        });
    }

    protected abstract void transfer(LogisticContainerComponent<TContainer> source);
}
