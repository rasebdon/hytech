package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.containers.LogisticContainer;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/// The whole transfer algorithm, once, for every resource type.
///
/// Energy and items each carried their own copy of this. The control flow was identical, the
/// neighbour filter was character-identical, and the fair-share loop appeared three times
/// across the two files. All of it is expressible against [LogisticContainer], so a new
/// resource type inherits pull, push, priority ordering, fair-share distribution and rate
/// limiting without writing any of it.
///
/// Each pass runs three phases:
///
/// 1. **pull** -- every network draws from the sources it is allowed to extract from
/// 2. **block push** -- extracting blocks push to their own neighbours, in priority order
/// 3. **network push** -- each network drains its buffer into its sinks
///
/// Blocks are visited in `TransferPriority` order (see [LogisticTransferSystem]), so when two
/// sources compete for one destination the higher-priority one is served first.
public abstract class AbstractTransferSystem<TContainer extends LogisticContainer>
        extends LogisticTransferSystem<TContainer> {

    private float sinceLastPass;

    protected AbstractTransferSystem(
            IEventRegistry eventRegistry,
            Class<? extends LogisticComponentChangedEvent<TContainer>> containerChangedEventClass,
            Class<? extends LogisticNetworkChangedEvent<TContainer>> networkChangedEventClass) {
        super(eventRegistry, containerChangedEventClass, networkChangedEventClass);
    }

    /// The slower end of a pair sets the pace.
    protected static long maxRate(LogisticContainer from, LogisticContainer to) {
        return Math.min(from.getTransferSpeed(), to.getTransferSpeed());
    }

    /// Seconds between transfer passes, or 0 to run every tick.
    ///
    /// This is per module rather than fixed because `MaxTransfer` is denominated per *pass*:
    /// energy moves its full rate every tick, items once a second. Unifying the two would
    /// rebalance every existing block, so the interval stays configurable and the unit stays
    /// documented on [LogisticContainer#getTransferSpeed].
    protected float getTransferIntervalSeconds() {
        return 0f;
    }

    /// Hook for resource types that track a per-pass delta for their UI. Energy is the only
    /// one today; the default does nothing so nobody pays for it.
    protected void onBeforePass(ContainerHolder<TContainer> holder) {
    }

    @Override
    public void tick(float dt, int systemIndex, @NotNull Store<ChunkStore> store) {
        float interval = getTransferIntervalSeconds();
        if (interval > 0f) {
            if (this.sinceLastPass < interval) {
                this.sinceLastPass += dt;
                return;
            }
            this.sinceLastPass = 0f;
        }

        for (var block : this.logisticBlockComponents) {
            onBeforePass(block);
        }
        for (var network : this.logisticNetworks) {
            onBeforePass(network);
        }

        for (var network : this.logisticNetworks) {
            pullIntoNetwork(network);
        }

        for (var block : this.logisticBlockComponents) {
            push(block, collectNeighbourTargets(block));
        }

        for (var network : this.logisticNetworks) {
            push(network, collectSinkTargets(network));
        }
    }

    /// Draws from every source the network is allowed to pull from.
    ///
    /// Overridable because a network is not a buffer for every resource: items must not be
    /// parked in a pipe, so the item module replaces this with a direct source-to-sink move.
    protected void pullIntoNetwork(LogisticNetwork<TContainer> network) {
        if (!network.isAvailable()) return;

        var buffer = network.getContainer();
        if (buffer == null || buffer.isFull()) return;

        // One budget for the whole pass, so a network with many sources cannot pull
        // (sources x speed) in a single tick.
        long budget = buffer.getTransferSpeed();

        for (var pullTarget : network.getPullTargets()) {
            if (budget <= 0L || buffer.isFull()) return;
            if (!pullTarget.isAvailable()) continue;

            var source = pullTarget.getContainer();
            if (source == null || source.isEmpty()) continue;

            budget -= source.moveTo(buffer, Math.min(budget, maxRate(source, buffer)));
        }
    }

    /// Neighbours this block may push into, given both sides' face configuration.
    private List<TContainer> collectNeighbourTargets(LogisticBlockComponent<TContainer> block) {
        if (!block.isAvailable() || !block.isExtracting()) return List.of();

        return block.getNeighbors().stream()
                .filter(n -> n.getHolder().isAvailable()
                        && block.hasOutputOrBothTowards(n.getHolder())
                        && n.allowsInputTowards(block))
                .map(n -> n.getHolder().getContainer())
                .filter(t -> t != null && !t.isFull())
                .distinct()
                .toList();
    }

    protected List<TContainer> collectSinkTargets(LogisticNetwork<TContainer> network) {
        if (!network.isAvailable()) return List.of();

        return network.getPushTargets().stream()
                .filter(ContainerHolder::isAvailable)
                .map(ContainerHolder::getContainer)
                .filter(t -> t != null && !t.isFull())
                .distinct()
                .toList();
    }

    /// Splits what the source can spare across the targets in equal shares, handing the
    /// leftover to the first few so integer division loses nothing.
    private void push(ContainerHolder<TContainer> holder, List<TContainer> targets) {
        if (targets.isEmpty()) return;

        var source = holder.getContainer();
        if (source == null || source.isEmpty()) return;

        // Capping by the source's own speed is what stops a block with N outputs emitting
        // N x MaxTransfer in one pass -- the bug the energy implementation had.
        long budget = Math.min(source.getAvailable(), source.getTransferSpeed());
        if (budget <= 0L) return;

        long demand = 0L;
        for (var target : targets) {
            demand = LogisticContainer.saturatingSum(demand, target.getAcceptable());
        }

        long transferable = Math.min(budget, demand);
        if (transferable <= 0L) return;

        int count = targets.size();
        long base = transferable / count;
        long remainder = transferable % count;

        for (int i = 0; i < count; i++) {
            if (source.isEmpty()) break;

            var target = targets.get(i);

            long share = base + (i < remainder ? 1L : 0L);
            if (share <= 0L) continue;

            source.moveTo(target, Math.min(share, maxRate(source, target)));
        }
    }
}
