package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.event.IEventRegistry;
import com.rasebdon.hytech.core.components.LogisticBlockComponent;
import com.rasebdon.hytech.core.systems.LogisticTransferSystem;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;

public class EnergyTransferSystem extends LogisticTransferSystem<IEnergyContainer> {
    public EnergyTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, EnergyContainerChangedEvent.class, EnergyNetworkChangedEvent.class);
    }

    @Override
    public void transfer(LogisticBlockComponent<IEnergyContainer> source) {
        var sourceContainer = source.getContainer();
        var sourceEnergy = sourceContainer.getEnergy();
        var sourceTransferSpeed = sourceContainer.getTransferSpeed();

        if (sourceEnergy <= 0 || sourceTransferSpeed <= 0) return;

        var validTargets = source.getTransferTargets().stream()
                .map(t -> t.target().getContainer())
                .filter(t -> t.getRemainingCapacity() > 0)
                .toList();

        if (validTargets.isEmpty()) return;

        long totalTransferred = 0;

        // TODO : Only transfer to pipes that are in Normal (Both) mode

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
