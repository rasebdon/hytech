package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.components.SingleBlockEnergyContainerComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

public class EnergyContainerTransferSystem extends TickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> singleBlockEnergyContainerComponentType;
    private final Archetype<ChunkStore> containerArchetype;

    public EnergyContainerTransferSystem(
            ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> singleBlockEnergyContainerComponentType) {
        this.singleBlockEnergyContainerComponentType = singleBlockEnergyContainerComponentType;
        containerArchetype = Archetype.of(singleBlockEnergyContainerComponentType);
    }

    // TODO : Will have dependency on network transfer system
    @Override
    public @NotNull Set<Dependency<ChunkStore>> getDependencies() {
        return super.getDependencies();
    }

    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        store.forEachChunk(this.containerArchetype, (a, _) -> {
            var containers = new SingleBlockEnergyContainerComponent[a.size()];

            for (int i = 0; i < a.size(); i++) {
                containers[i] = a.getComponent(i, this.singleBlockEnergyContainerComponentType);
            }

            containers = Arrays.stream(containers)
                    .sorted(Comparator.comparingInt(SingleBlockEnergyContainerComponent::getTransferPriority))
                    .toArray(SingleBlockEnergyContainerComponent[]::new);

            for (var container : containers) {
                container.tryTransferToTargets();
            }
        });
    }
}
