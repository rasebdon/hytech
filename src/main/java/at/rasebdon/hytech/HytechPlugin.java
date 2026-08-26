package at.rasebdon.hytech;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.fluid.FluidModule;
import at.rasebdon.hytech.gas.GasModule;
import at.rasebdon.hytech.heat.HeatModule;
import at.rasebdon.hytech.items.ItemModule;
import at.rasebdon.hytech.machines.MachineModule;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

// Thanks to notnotnotswipez for supporting on the official Hytale Discord

@SuppressWarnings("unused")
public final class HytechPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HytechPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        var entityStoreRegistry = this.getEntityStoreRegistry();
        var chunkStoreRegistry = this.getChunkStoreRegistry();
        var eventRegistry = this.getEventRegistry();

        HytechCoreModule.init(entityStoreRegistry, chunkStoreRegistry);

        // Items before energy: the burner generator burns items for energy, so energy is the
        // module with the dependency. Nothing on the item side needs energy.
        ItemModule.init(chunkStoreRegistry, eventRegistry);
        EnergyModule.init(chunkStoreRegistry, eventRegistry);
        HeatModule.init(chunkStoreRegistry, eventRegistry);
        FluidModule.init(chunkStoreRegistry, eventRegistry);
        GasModule.init(chunkStoreRegistry, eventRegistry);

        // Machines last: they own neither slots nor a buffer, they read the item and energy
        // components of the block they sit on, so both those modules have to exist first.
        MachineModule.init(chunkStoreRegistry);
    }
}
