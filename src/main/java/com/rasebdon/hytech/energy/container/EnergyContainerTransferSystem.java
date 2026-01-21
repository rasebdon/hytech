package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

public class EnergyContainerTransferSystem extends TickingSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<ChunkStore, EnergyContainerComponent> energyContainerType;
    private final Archetype<ChunkStore> containerArchetype;

    public EnergyContainerTransferSystem(ComponentType<ChunkStore, EnergyContainerComponent> energyContainerType) {
        this.energyContainerType = energyContainerType;
        containerArchetype = Archetype.of(energyContainerType);
    }

    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        store.forEachChunk(this.containerArchetype, (a, v) -> {
            var containers = new EnergyContainerComponent[a.size()];

            for (int i = 0; i < a.size(); i++) {
                containers[i] = a.getComponent(i, this.energyContainerType);
            }

            containers = Arrays.stream(containers)
                    .sorted(Comparator.comparingInt(EnergyContainerComponent::getTransferPriority))
                    .toArray(EnergyContainerComponent[]::new);

            for (var container : containers) {
                container.tryTransferToTargets();
            }
        });
    }
}
