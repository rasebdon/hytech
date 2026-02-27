package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import at.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

public class EnergyTransferSystem extends LogisticTransferSystem<HytechEnergyContainer> {
    public EnergyTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, EnergyContainerChangedEvent.class, EnergyNetworkChangedEvent.class);
    }

    private static void transfer(
            HytechEnergyContainer from,
            HytechEnergyContainer to,
            long maxAmount
    ) {
        if (maxAmount <= 0) return;
        if (from.isEmpty() || to.isFull()) return;

        long transferable = Math.min(
                maxAmount,
                Math.min(from.getEnergy(), to.getRemainingCapacity())
        );

        if (transferable <= 0) return;

        from.reduceEnergy(transferable);
        to.addEnergy(transferable);
    }

    private static long maxRate(HytechEnergyContainer a, HytechEnergyContainer b) {
        return Math.min(a.getTransferSpeed(), b.getTransferSpeed());
    }

    @Override
    public void tick(float dt, int index, @NotNull Store<ChunkStore> store) {
        for (var block : logisticBlockComponents) {
            updateEnergyDelta(block);
        }

        for (var network : logisticNetworks) {
            updateEnergyDelta(network);
        }

        for (var network : logisticNetworks) {
            pullIntoNetworkMaxRate(network);
        }

        for (var block : logisticBlockComponents) {
            balancedBlockPush(block);
        }

        for (var network : logisticNetworks) {
            balancedNetworkPush(network);
        }
    }

    private void updateEnergyDelta(ContainerHolder<HytechEnergyContainer> holder) {
        if (!holder.isAvailable()) return;
        holder.getContainer().updateEnergyDelta();
    }

    private void pullIntoNetworkMaxRate(LogisticNetwork<HytechEnergyContainer> network) {
        if (!network.isAvailable()) return;

        var netContainer = network.getContainer();
        if (netContainer.isFull()) return;

        for (var target : network.getPullTargets()) {

            if (!target.isAvailable()) continue;
            if (netContainer.isFull()) break;

            var source = target.getContainer();

            long rate = maxRate(source, netContainer);

            transfer(source, netContainer, rate);
        }
    }

    private void balancedBlockPush(LogisticBlockComponent<HytechEnergyContainer> block) {
        if (!block.isAvailable() || !block.isExtracting()) return;

        var source = block.getContainer();
        if (source.isEmpty()) return;

        var targets = block.getNeighbors().stream()
                .filter(n -> n.getHolder().isAvailable() &&
                        block.hasOutputOrBothTowards(n.getHolder()) &&
                        n.allowsInputTowards(block))
                .map(n -> n.getHolder().getContainer())
                .filter(t -> !t.isFull())
                .distinct()
                .toList();

        if (targets.isEmpty()) return;

        long totalDemand = 0;
        for (var t : targets) {
            totalDemand += t.getRemainingCapacity();
        }

        long transferable = Math.min(source.getEnergy(), totalDemand);
        if (transferable <= 0) return;

        int count = targets.size();
        long base = transferable / count;
        long remainder = transferable % count;

        for (int i = 0; i < count; i++) {

            if (source.isEmpty()) break;

            var target = targets.get(i);

            long share = base + (i < remainder ? 1 : 0);
            long rateLimited = Math.min(share, maxRate(source, target));

            transfer(source, target, rateLimited);
        }
    }

    private void balancedNetworkPush(LogisticNetwork<HytechEnergyContainer> network) {
        if (!network.isAvailable()) return;

        var netContainer = network.getContainer();
        if (netContainer.isEmpty()) return;

        var targets = network.getPushTargets().stream()
                .filter(ContainerHolder::isAvailable)
                .map(ContainerHolder::getContainer)
                .filter(t -> !t.isFull())
                .toList();

        if (targets.isEmpty()) return;

        long totalDemand = 0;
        for (var t : targets) {
            totalDemand += t.getRemainingCapacity();
        }

        long transferable = Math.min(netContainer.getEnergy(), totalDemand);
        if (transferable <= 0) return;

        int count = targets.size();
        long base = transferable / count;
        long remainder = transferable % count;

        for (int i = 0; i < count; i++) {

            if (netContainer.isEmpty()) break;

            var target = targets.get(i);

            long share = base + (i < remainder ? 1 : 0);
            long rateLimited = Math.min(share, maxRate(netContainer, target));

            transfer(netContainer, target, rateLimited);
        }
    }

//    private void pushEnergyOutOfNetwork(LogisticNetwork<HytechEnergyContainer> network) {
//        if (!network.isAvailable()) return;
//
//        var targets = network.getPushTargets();
//        if (targets.isEmpty()) return;
//
//        var networkContainer = network.getContainer();
//
//        for (var target : targets) {
//            if (!target.isAvailable()) continue;
//
//            transfer(networkContainer, target.getContainer());
//        }
//    }
//
//    public void transferBlockEnergyToNeighbors(LogisticBlockComponent<HytechEnergyContainer> energyBlock) {
//        if (!energyBlock.isAvailable() || !energyBlock.isExtracting()) return;
//
//        var energyBlockContainer = energyBlock.getContainer();
//
//
//        for (var neighbor : energyBlock.getNeighbors()) {
//            var neighborHolder = neighbor.getHolder();
//
//            if (!neighborHolder.isAvailable()) continue;
//
//            var neighborContainer = neighborHolder.getContainer();
//
//            if (neighborContainer.isFull()) continue;
//
//            if (!energyBlock.hasOutputOrBothTowards(neighbor.getHolder()) ||
//                    !neighbor.allowsInputTowards(energyBlock)) {
//                continue;
//            }
//
//            transferEnergy(energyBlockContainer, neighborContainer);
//        }
//    }
}
