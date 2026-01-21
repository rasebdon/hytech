package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
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
                    .sorted(Comparator.comparingInt(EnergyContainerComponent::getExtractPriority))
                    .toArray(EnergyContainerComponent[]::new);

            for (var container : containers) {
                container.tryExtractToTargets();
            }
        });
    }
}
