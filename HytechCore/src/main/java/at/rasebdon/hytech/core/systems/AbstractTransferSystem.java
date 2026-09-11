package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.containers.LogisticContainer;
import at.rasebdon.hytech.core.containers.ScalarContainer;
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
/// Each pass runs three phases: **pull** (networks draw from allowed sources), **block push**
/// (extracting blocks push to neighbours, in `TransferPriority` order), then **network push**
/// (each network drains into its sinks).
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

    /// Seconds between transfer passes, or 0 to run every tick. Per module because `MaxTransfer`
    /// is denominated per pass, not per second -- energy ticks every frame, items once a second.
    protected float getTransferIntervalSeconds() {
        return 0f;
    }

    /// Snapshots each container's per-pass delta, which the UIs read as a throughput figure.
    protected void onBeforePass(ContainerHolder<TContainer> holder) {
        if (!holder.isAvailable()) return;

        if (holder.getContainer() instanceof ScalarContainer scalar) {
            scalar.updateDelta();
        }
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

    /// Overridable: items must not be parked in a pipe, so the item module replaces this with a
    /// direct source-to-sink move.
    protected void pullIntoNetwork(LogisticNetwork<TContainer> network) {
        if (!network.isAvailable()) return;

        var buffer = network.getContainer();
        if (buffer == null || buffer.isFull()) return;

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

        // Caps by the source's own speed, so N outputs cannot emit N x MaxTransfer in one pass.
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
