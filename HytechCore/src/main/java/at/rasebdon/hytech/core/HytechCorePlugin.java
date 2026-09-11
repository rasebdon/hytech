package at.rasebdon.hytech.core;

import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.fluid.FluidModule;
import at.rasebdon.hytech.gas.GasModule;
import at.rasebdon.hytech.heat.HeatModule;
import at.rasebdon.hytech.items.ItemModule;
import at.rasebdon.hytech.machines.MachineModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

// Thanks to notnotnotswipez for supporting on the official Hytale Discord

/// The logistics library every tech mod builds on: the framework, the five resource types and the
/// machine engine, and no concrete block.
@SuppressWarnings("unused")
public final class HytechCorePlugin extends JavaPlugin {
    public HytechCorePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        var entityStoreRegistry = this.getEntityStoreRegistry();
        var chunkStoreRegistry = this.getChunkStoreRegistry();
        var eventRegistry = this.getEventRegistry();

        HytechCoreModule.init(entityStoreRegistry, chunkStoreRegistry);

        // Items before energy: a burner generator burns items for energy, the reverse dependency
        // never holds. Registration order also decides wrench/side-panel ordering.
        ItemModule.init(chunkStoreRegistry, eventRegistry);
        EnergyModule.init(chunkStoreRegistry, eventRegistry);
        HeatModule.init(chunkStoreRegistry, eventRegistry);
        FluidModule.init(chunkStoreRegistry, eventRegistry);
        GasModule.init(chunkStoreRegistry, eventRegistry);

        // Machines last: a machine reads the item and energy components of the block it sits on.
        MachineModule.init(chunkStoreRegistry);
    }
}
