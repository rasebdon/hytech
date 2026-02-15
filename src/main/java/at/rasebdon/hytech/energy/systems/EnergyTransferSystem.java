package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.energy.IEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import at.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

public class EnergyTransferSystem extends LogisticTransferSystem<IEnergyContainer> {
    public EnergyTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, EnergyContainerChangedEvent.class, EnergyNetworkChangedEvent.class);
    }

    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        for (var container : containerComponents) {
            container.getContainer().updateEnergyDelta();
        }

        for (var network : networks) {
            network.getContainer().updateEnergyDelta();
        }

        super.tick(dt, index, store);
    }

    @Override
    public void transfer(LogisticBlockComponent<IEnergyContainer> sourceComponent) {
        if (!sourceComponent.isAvailable() || !sourceComponent.isExtracting()) return;

        var sourceContainer = sourceComponent.getContainer();
        var sourceEnergy = sourceContainer.getEnergy();
        var sourceTransferSpeed = sourceContainer.getTransferSpeed();

        if (sourceEnergy <= 0 || sourceTransferSpeed <= 0) return;

        var neighborsWithEnergyCapacityLeft = sourceComponent.getNeighbors().stream()
                .filter(n ->
                        n.isAvailable() && n.getContainer().getRemainingCapacity() > 0)
                .toList();

        long totalTransferred = 0;

        for (var neighbor : neighborsWithEnergyCapacityLeft) {
            if (sourceEnergy <= 0) break;

            if (!sourceComponent.hasOutputFaceTowards(neighbor) || !neighbor.hasInputFaceTowards(sourceComponent)) {
                continue;
            }

            var targetContainer = neighbor.getContainer();
            long transferable = Math.min(
                    Math.min(sourceTransferSpeed, targetContainer.getTransferSpeed()),
                    Math.min(sourceEnergy, targetContainer.getRemainingCapacity())
            );

            if (transferable > 0) {
                targetContainer.addEnergy(transferable);
                sourceEnergy -= transferable;
                totalTransferred += transferable;
            }
        }

        sourceContainer.reduceEnergy(totalTransferred);
    }
}
