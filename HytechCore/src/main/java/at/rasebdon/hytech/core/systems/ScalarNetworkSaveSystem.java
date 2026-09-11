package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/// Periodically writes a network's contents back onto its pipes so they persist with the
/// blocks. Subclassed per resource type since `ComponentRegistry` keys systems by class.
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

    /// Distributes the network's contents across its pipes, weighted by capacity. Any remainder
    /// from integer division carries into the following pipes rather than being dropped.
    private void save(LogisticNetwork<TContainer> network) {
        var container = network.getContainer();
        if (container == null) return;

        // Raw type: a wildcard element cannot be collected into a List without the capture leaking.
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

            // Keeps zero-capacity pipes out of the divisor.
            if (capacity <= 0L) {
                writePipe(pipe, 0L, network);
                continue;
            }

            // Proportional to what remains of both contents and capacity, so the final pipe
            // gets exactly the remainder instead of a rounded-down share.
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

    /// Abstract because the container type is only known to the concrete resource module.
    protected abstract long amountOf(LogisticNetwork<TContainer> network);

    /// Overridden by typed resources, which must also record *what* the segment holds.
    protected void writePipe(
            AbstractScalarPipeComponent pipe,
            long amount,
            LogisticNetwork<TContainer> network) {
        pipe.setSavedAmount(amount);
    }
}
