package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Item transfer. The algorithm lives in [AbstractTransferSystem]; the item-specific parts
/// -- source slot choice and stack merging -- live in [HytechItemContainer#moveTo], which
/// delegates them to the vanilla container.
public class ItemTransferSystem extends AbstractTransferSystem<HytechItemContainer> {

    public ItemTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, ItemContainerChangedEvent.class, ItemNetworkChangedEvent.class);
    }

    /// Items move once a second rather than every tick, so a pipe run reads as a visible
    /// conveyor rather than teleporting. `MaxTransfer` is therefore per second for items.
    @Override
    protected float getTransferIntervalSeconds() {
        return 1f;
    }

    /// Moves items straight from source to sink, leaving the pipes empty.
    ///
    /// The inherited pull draws into the network's own buffer and pushes it out in a later
    /// phase, which is right for a scalar: energy in a cable is invisible and costs nothing.
    /// For items that buffer is a trap. The pull is bounded by the run's transfer speed, not
    /// by what the sinks can take -- item containers cannot report a meaningful remaining
    /// capacity ([HytechItemContainer#getAcceptable]) -- so a fast pipe emptied a chest into
    /// its own slots, the sink took the one stack it had room for, and the rest sat in the
    /// pipe out of everyone's reach until [ItemPipeEjectSystem] threw it on the floor.
    ///
    /// Routing each stack directly asks the destination container itself how much it will
    /// take, which is the only component that actually knows. Pipes stay a conduit: they
    /// decide *what reaches what* and how fast, and hold nothing.
    @Override
    protected void pullIntoNetwork(LogisticNetwork<HytechItemContainer> network) {
        if (!network.isAvailable()) return;

        // The run itself still sets the pace, so a slow pipe throttles the whole line even
        // though nothing is stored in it.
        var pipes = network.getContainer();
        if (pipes == null) return;

        long budget = pipes.getTransferSpeed();
        if (budget <= 0L) return;

        var sinks = collectSinkTargets(network);
        if (sinks.isEmpty()) return;

        for (var pullTarget : network.getPullTargets()) {
            if (budget <= 0L) return;
            if (!pullTarget.isAvailable()) continue;

            var source = pullTarget.getContainer();
            if (source == null || source.isEmpty()) continue;

            for (var sink : sinks) {
                if (budget <= 0L || source.isEmpty()) break;
                if (sink == source || sink.isFull()) continue;

                long rate = Math.min(budget, Math.min(maxRate(source, sink), pipes.getTransferSpeed()));
                if (rate <= 0L) continue;

                budget -= source.moveTo(sink, rate);
            }
        }
    }
}
