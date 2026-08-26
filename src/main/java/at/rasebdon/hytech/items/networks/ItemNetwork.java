package at.rasebdon.hytech.items.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.components.ItemPipeComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Set;

/// A run of connected item pipes, presented as one container.
///
/// The buffer is not stored here: it is the union of the member pipes' own containers,
/// exposed through a [CombinedItemContainer]. That keeps persistence entirely in the pipe
/// components (see [ItemPipeComponent]) so nothing has to be redistributed on save, and
/// it means the aggregate view is always consistent with what the pipes actually hold.
public class ItemNetwork extends LogisticNetwork<HytechItemContainer> implements HytechItemContainer {

    @Nullable
    private ItemContainer combinedContainer;
    private long transferSpeed;

    public ItemNetwork(Set<LogisticPipeComponent<HytechItemContainer>> initialPipes) {
        super(initialPipes);
        recalculateStats();
    }

    @Override
    protected void onPipesChanged() {
        recalculateStats();
    }

    @Override
    public void reload() {
        recalculateStats();
    }

    /// Rebuilds the aggregate container and the network's rate limit. The slowest pipe in
    /// the run sets the pace, matching how the energy network derives its transfer speed.
    private void recalculateStats() {
        var containers = new ArrayList<ItemContainer>(pipes.size());
        long minSpeed = Long.MAX_VALUE;

        for (var pipe : pipes) {
            if (!(pipe instanceof ItemPipeComponent itemPipe)) continue;

            var container = itemPipe.getItemContainer();
            if (container != null) {
                containers.add(container);
            }

            minSpeed = Math.min(minSpeed, itemPipe.getTransferSpeed());
        }

        this.combinedContainer = containers.isEmpty()
                ? null
                : new CombinedItemContainer(containers.toArray(ItemContainer[]::new));
        this.transferSpeed = minSpeed == Long.MAX_VALUE ? 0L : minSpeed;
    }

    @Override
    public HytechItemContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return this.combinedContainer != null;
    }

    @Override
    public ItemContainer getItemContainer() {
        return this.combinedContainer == null ? EmptyItemContainer.INSTANCE : this.combinedContainer;
    }

    @Override
    public long getTransferSpeed() {
        return transferSpeed;
    }

    /// A run with nowhere to deliver cannot accept anything.
    ///
    /// Item pipes are a conduit, not storage: the transfer system refuses to draw into them
    /// without a sink, and this says the same thing to everyone *else* who might insert -- a
    /// machine with auto-push on, or a block pushing to its neighbours. Without it, switching
    /// auto-push on next to a dead-end pipe would load the run and
    /// [at.rasebdon.hytech.items.systems.ItemPipeEjectSystem] would put the contents on the floor
    /// three seconds later.
    @Override
    public boolean isFull() {
        return !hasReachableSink() || HytechItemContainer.super.isFull();
    }

    /// Whether any push target could take something. Targets never include pipes, so this asks
    /// the blocks and wrapped containers at the ends of the run and cannot recurse.
    private boolean hasReachableSink() {
        for (var target : getPushTargets()) {
            if (!target.isAvailable()) continue;

            var container = target.getContainer();
            if (container != null && !container.isFull()) return true;
        }

        return false;
    }
}
