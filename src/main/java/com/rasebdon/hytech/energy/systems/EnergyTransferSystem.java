package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.systems.LogisticTransferSystem;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import com.rasebdon.hytech.energy.EnergyContainer;

public class EnergyTransferSystem extends LogisticTransferSystem<EnergyContainer> {
    public EnergyTransferSystem(
            ComponentType<ChunkStore, ? extends LogisticContainerComponent<EnergyContainer>> containerComponentType
    ) {
        super(containerComponentType);
    }

    @Override
    public void transfer(LogisticContainerComponent<EnergyContainer> source) {
        var sourceContainer = source.getContainer();
        var sourceEnergy = sourceContainer.getEnergy();
        var sourceTransferSpeed = sourceContainer.getTransferSpeed();

        if (sourceEnergy <= 0 || sourceTransferSpeed <= 0) return;

        var validTargets = source.getTransferTargets().stream()
                .map(LogisticTransferTarget::target)
                .filter(t -> t.getRemainingCapacity() > 0)
                .toList();

        if (validTargets.isEmpty()) return;

        long totalTransferred = 0;

        for (var target : validTargets) {
            if (sourceEnergy <= 0) break;

            long transferable = Math.min(
                    Math.min(sourceTransferSpeed, target.getTransferSpeed()),
                    Math.min(sourceEnergy, target.getRemainingCapacity())
            );

            if (transferable > 0) {
                target.addEnergy(transferable);
                sourceEnergy -= transferable;
                totalTransferred += transferable;
            }
        }

        sourceContainer.reduceEnergy(totalTransferred);
    }
}
