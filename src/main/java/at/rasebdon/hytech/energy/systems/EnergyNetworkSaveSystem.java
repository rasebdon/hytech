package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

public class EnergyNetworkSaveSystem extends TickingSystem<ChunkStore> {

    private static final int SAVE_INTERVAL_SECONDS = 5; // every 5 seconds
    private final LogisticNetworkSystem<HytechEnergyContainer> energyNetworkSystem;
    private float seconds;

    public EnergyNetworkSaveSystem(LogisticNetworkSystem<HytechEnergyContainer> energyNetworkSystem) {
        this.energyNetworkSystem = energyNetworkSystem;
    }

    @Override
    public void tick(float dt, int systemIndex, @NotNull Store<ChunkStore> store) {
        seconds += dt;

        if (seconds >= SAVE_INTERVAL_SECONDS) {
            seconds = 0;
            saveAllNetworks();
        }
    }

    private void saveAllNetworks() {
        for (var network : energyNetworkSystem.getNetworks()) {
            saveNetwork(network);
        }
    }

    /// Spreads the network's energy back across its pipes so it persists with the blocks.
    ///
    /// Distribution is weighted by each pipe's own capacity, and whatever integer division
    /// leaves over is carried into the following pipes. The previous version divided evenly
    /// and dropped the remainder, and [at.rasebdon.hytech.energy.networks.EnergyNetwork]
    /// reads these values straight back -- so every save silently destroyed up to
    /// (pipeCount - 1) energy, and a mixed-capacity run over-filled its small pipes and
    /// clipped the excess. Both losses compounded every five seconds.
    private void saveNetwork(LogisticNetwork<HytechEnergyContainer> network) {
        var networkContainer = network.getContainer();
        if (networkContainer == null) return;

        var pipes = network.getPipes().stream()
                .filter(EnergyPipeComponent.class::isInstance)
                .map(EnergyPipeComponent.class::cast)
                .toList();

        if (pipes.isEmpty()) return;

        long totalCapacity = 0L;
        for (var pipe : pipes) {
            totalCapacity += pipe.getPipeCapacity();
        }

        long remaining = Math.min(networkContainer.getAmount(), totalCapacity);

        if (totalCapacity <= 0L) {
            for (var pipe : pipes) {
                pipe.setSavedEnergy(0L);
            }
            return;
        }

        for (var pipe : pipes) {
            long capacity = pipe.getPipeCapacity();

            // A zero-capacity pipe holds nothing, and skipping it also keeps it out of the
            // divisor below -- which would otherwise be zero if such a pipe came last.
            if (capacity <= 0L) {
                pipe.setSavedEnergy(0L);
                continue;
            }

            // Weighted share, floored -- the shortfall stays in `remaining` and lands on a
            // later pipe rather than evaporating. Exact integer math: both factors are
            // capacities validated non-negative, and their product stays well inside long
            // for any capacity a block can realistically declare.
            long share = Math.min(capacity, remaining * capacity / totalCapacity);

            pipe.setSavedEnergy(share);
            remaining -= share;
            totalCapacity -= capacity;
        }

        // Anything left after rounding goes wherever there is still room.
        if (remaining > 0L) {
            for (var pipe : pipes) {
                if (remaining <= 0L) break;

                long room = pipe.getPipeCapacity() - pipe.getSavedEnergy();
                if (room <= 0L) continue;

                long extra = Math.min(room, remaining);
                pipe.setSavedEnergy(pipe.getSavedEnergy() + extra);
                remaining -= extra;
            }
        }
    }
}
