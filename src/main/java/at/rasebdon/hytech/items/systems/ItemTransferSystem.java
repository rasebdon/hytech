package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/// Moves items the same way [at.rasebdon.hytech.energy.systems.EnergyTransferSystem] moves
/// energy: pull from extracting sources into the network, then push the network's buffer
/// out across its sinks in fair shares, with both ends rate limited.
///
/// The item-specific parts -- picking source slots and merging into existing stacks -- are
/// handled by the vanilla container inside [HytechItemContainer#moveTo].
public class ItemTransferSystem extends LogisticTransferSystem<HytechItemContainer> {

    /// Seconds between transfer passes. Time based rather than tick counted, matching the
    /// other Hytech systems.
    private static final float TRANSFER_INTERVAL_SECONDS = 1f;

    private float transferTime;

    public ItemTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, ItemContainerChangedEvent.class, ItemNetworkChangedEvent.class);
    }

    /// The slower end of a pair sets the pace, as on the energy side.
    private static long maxRate(HytechItemContainer from, HytechItemContainer to) {
        return Math.min(from.getTransferSpeed(), to.getTransferSpeed());
    }

    @Override
    public void tick(float dt, int systemIndex, @NotNull Store<ChunkStore> store) {
        if (this.transferTime < TRANSFER_INTERVAL_SECONDS) {
            this.transferTime += dt;
            return;
        }

        this.transferTime = 0f;

        for (var network : this.logisticNetworks) {
            pullIntoNetwork(network);
        }

        // Blocks are kept sorted by transfer priority, so higher priority sources get
        // first call on the destinations they share.
        for (var block : this.logisticBlockComponents) {
            balancedBlockPush(block);
        }

        for (var network : this.logisticNetworks) {
            balancedNetworkPush(network);
        }
    }

    /// Draws from every source the network is allowed to pull from.
    private void pullIntoNetwork(LogisticNetwork<HytechItemContainer> network) {
        if (!network.isAvailable()) return;

        var netContainer = network.getContainer();
        if (netContainer.isFull()) return;

        for (var pullTarget : network.getPullTargets()) {
            if (!pullTarget.isAvailable()) continue;

            var source = pullTarget.getContainer();
            if (source == null || source.isEmpty()) continue;

            source.moveTo(netContainer, maxRate(source, netContainer));

            if (netContainer.isFull()) return;
        }
    }

    /// Pushes an extracting block's contents out to its neighbours in equal shares.
    private void balancedBlockPush(LogisticBlockComponent<HytechItemContainer> block) {
        if (!block.isAvailable() || !block.isExtracting()) return;

        var source = block.getContainer();
        if (source == null || source.isEmpty()) return;

        var targets = block.getNeighbors().stream()
                .filter(n -> n.getHolder().isAvailable()
                        && block.hasOutputOrBothTowards(n.getHolder())
                        && n.allowsInputTowards(block))
                .map(n -> n.getHolder().getContainer())
                .filter(t -> t != null && !t.isFull())
                .distinct()
                .toList();

        distribute(source, targets);
    }

    /// Pushes the network buffer out to its sinks in equal shares.
    private void balancedNetworkPush(LogisticNetwork<HytechItemContainer> network) {
        if (!network.isAvailable()) return;

        var netContainer = network.getContainer();
        if (netContainer.isEmpty()) return;

        var targets = network.getPushTargets().stream()
                .filter(ContainerHolder::isAvailable)
                .map(ContainerHolder::getContainer)
                .filter(t -> t != null && !t.isFull())
                .distinct()
                .toList();

        distribute(netContainer, targets);
    }

    /// Splits what the source can spare evenly across the targets, giving the leftover to
    /// the first few so nothing is lost to integer division.
    private void distribute(HytechItemContainer source, List<HytechItemContainer> targets) {
        if (targets.isEmpty()) return;

        long available = Math.min(source.getItemCount(), source.getTransferSpeed());
        if (available <= 0) return;

        int count = targets.size();
        long base = available / count;
        long remainder = available % count;

        for (int i = 0; i < count; i++) {
            if (source.isEmpty()) break;

            var target = targets.get(i);

            long share = base + (i < remainder ? 1 : 0);
            if (share <= 0) continue;

            source.moveTo(target, Math.min(share, maxRate(source, target)));
        }
    }
}
