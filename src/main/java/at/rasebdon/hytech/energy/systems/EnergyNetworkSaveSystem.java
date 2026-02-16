package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.energy.EnergyContainer;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;

public class EnergyNetworkSaveSystem extends TickingSystem<ChunkStore> {

    private static final int SAVE_INTERVAL_SECONDS = 5; // every 5 seconds
    private final LogisticNetworkSystem<EnergyContainer> energyNetworkSystem;
    private float seconds;

    public EnergyNetworkSaveSystem(LogisticNetworkSystem<EnergyContainer> energyNetworkSystem) {
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

    private void saveNetwork(LogisticNetwork<EnergyContainer> network) {
        var networkContainer = network.getContainer();
        long energy = networkContainer.getEnergy();
        // TODO : Account for different pipe energy capacities
        long perPipe = network.getPipes().isEmpty() ? 0 : energy / network.getPipes().size();

        for (var pipe : network.getPipes()) {
            if (pipe instanceof EnergyPipeComponent energyPipe) {
                energyPipe.setSavedEnergy(perPipe);
            }
        }
    }
}
