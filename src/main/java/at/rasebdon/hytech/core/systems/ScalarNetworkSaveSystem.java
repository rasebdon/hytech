package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/// Periodically writes a network's contents back onto its pipes, so they persist with the
/// blocks rather than only in the live network object.
///
/// Subclassed per resource type because `ComponentRegistry` keys systems by class.
@SuppressWarnings("rawtypes")
public abstract class ScalarNetworkSaveSystem<TContainer> extends TickingSystem<ChunkStore> {

    private static final float SAVE_INTERVAL_SECONDS = 5f;

    private final LogisticNetworkSystem<TContainer> networkSystem;
    private float seconds;

    protected ScalarNetworkSaveSystem(LogisticNetworkSystem<TContainer> networkSystem) {
        this.networkSystem = networkSystem;
    }

    @Override
    public void tick(float dt, int systemIndex, @NotNull Store<ChunkStore> store) {
        this.seconds += dt;

        if (this.seconds < SAVE_INTERVAL_SECONDS) return;

        this.seconds = 0f;

        for (var network : this.networkSystem.getNetworks()) {
            save(network);
        }
    }

    /// Distributes the network's contents across its pipes, weighted by capacity.
    ///
    /// Whatever integer division leaves over is carried into the following pipes rather than
    /// dropped. The original energy implementation divided evenly and discarded the remainder
    /// while the network read those rounded values straight back, so every save destroyed up
    /// to (pipeCount - 1) units and a mixed-capacity run over-filled its small pipes and
    /// clipped the excess. Both losses compounded every five seconds.
    private void save(LogisticNetwork<TContainer> network) {
        var container = network.getContainer();
        if (container == null) return;

        // Raw element type: only the capacity/amount accessors are used here, none of which
        // mention the container type, and a wildcard element cannot be collected into a List
        // without the capture leaking into the stream's type.
        List<AbstractScalarPipeComponent> pipes = network.getPipes().stream()
                .filter(AbstractScalarPipeComponent.class::isInstance)
                .map(AbstractScalarPipeComponent.class::cast)
                .toList();

        if (pipes.isEmpty()) return;

        long totalCapacity = 0L;
        for (var pipe : pipes) {
            totalCapacity += pipe.getPipeCapacity();
        }

        if (totalCapacity <= 0L) {
            for (var pipe : pipes) {
                writePipe(pipe, 0L, network);
            }
            return;
        }

        long remaining = Math.min(amountOf(network), totalCapacity);

        for (var pipe : pipes) {
            long capacity = pipe.getPipeCapacity();

            // Skipping empty pipes also keeps them out of the divisor, which would otherwise
            // be zero if a zero-capacity pipe sorted last.
            if (capacity <= 0L) {
                writePipe(pipe, 0L, network);
                continue;
            }

            // Proportional to what is left of both the contents and the capacity, so the
            // final pipe receives exactly the remainder instead of a rounded-down share.
            long share = Math.min(capacity, remaining * capacity / totalCapacity);

            writePipe(pipe, share, network);
            remaining -= share;
            totalCapacity -= capacity;
        }

        // Anything still left after capacity clamping goes wherever there is room.
        for (var pipe : pipes) {
            if (remaining <= 0L) break;

            long room = pipe.getPipeCapacity() - pipe.getSavedAmount();
            if (room <= 0L) continue;

            long extra = Math.min(room, remaining);
            writePipe(pipe, pipe.getSavedAmount() + extra, network);
            remaining -= extra;
        }
    }

    /// The network's current contents. Abstract because the container type is only known to
    /// the concrete resource module.
    protected abstract long amountOf(LogisticNetwork<TContainer> network);

    /// Writes one pipe's share. Overridden by typed resources, which must also record *what*
    /// the segment is holding, not just how much.
    protected void writePipe(
            AbstractScalarPipeComponent pipe,
            long amount,
            LogisticNetwork<TContainer> network) {
        pipe.setSavedAmount(amount);
    }
}
