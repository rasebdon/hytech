package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

public class ItemTransferSystem extends AbstractTransferSystem<HytechItemContainer> {

    public ItemTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, ItemContainerChangedEvent.class, ItemNetworkChangedEvent.class);
    }

    @Override
    protected float getTransferIntervalSeconds() {
        return 1f;
    }

    /// Moves items straight from source to sink instead of through the network's own buffer:
    /// item containers can't report a meaningful [HytechItemContainer#getAcceptable], so
    /// buffering would overfill the pipe and strand the rest for [ItemPipeEjectSystem] to eject.
    @Override
    protected void pullIntoNetwork(LogisticNetwork<HytechItemContainer> network) {
        if (!network.isAvailable()) return;

        // The run still sets the pace even though nothing is stored in it.
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
