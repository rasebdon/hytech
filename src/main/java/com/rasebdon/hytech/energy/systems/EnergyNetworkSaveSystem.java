package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.components.EnergyPipeComponent;
import com.rasebdon.hytech.energy.networks.EnergyNetwork;
import com.rasebdon.hytech.energy.networks.EnergyNetworkSystem;
import org.jetbrains.annotations.NotNull;

public class EnergyNetworkSaveSystem extends TickingSystem<ChunkStore> {

    private static final int SAVE_INTERVAL_SECONDS = 5; // every 5 seconds
    private final EnergyNetworkSystem energyNetworkSystem;
    private float seconds;

    public EnergyNetworkSaveSystem(EnergyNetworkSystem energyNetworkSystem) {
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
        for (EnergyNetwork network : energyNetworkSystem.getNetworks()) {
            saveNetwork(network);
        }
    }

    private void saveNetwork(EnergyNetwork network) {
        long energy = network.getEnergy();
        // TODO : Account for different pipe energy capacities
        long perPipe = network.getPipes().isEmpty() ? 0 : energy / network.getPipes().size();

        for (var pipe : network.getPipes()) {
            if (pipe instanceof EnergyPipeComponent energyPipe) {
                energyPipe.setSavedEnergy(perPipe);
            }
        }
    }
}
