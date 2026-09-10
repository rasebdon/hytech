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

/// The logistics library every tech mod builds on.
///
/// This plugin owns the framework, the five resource types and the machine engine, and no concrete
/// block. A battery, a generator, a crusher and a cable are all content: they are specialized
/// implementations of a [at.rasebdon.hytech.core.containers.LogisticContainer] and differ between
/// mods. What ships here is what they are built *from*, which is what makes two mods interoperate
/// -- a generator from one and a machine from another meet on the same `hytech:energy:container`
/// and the same network.
///
/// A content plugin declares `"Technic:HytechCore"` in its manifest `Dependencies`, which both
/// guarantees this `setup()` has run before its own and lets it link against these classes through
/// `PluginManager.PluginBridgeClassLoader`.
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

        // Items before energy: a burner generator burns items for energy, so energy is the module
        // with the dependency. Nothing on the item side needs energy. The burner itself lives in a
        // content plugin now, but the ordering is still the resource types' own -- registration
        // order decides the order the wrench and the side panel offer them in.
        ItemModule.init(chunkStoreRegistry, eventRegistry);
        EnergyModule.init(chunkStoreRegistry, eventRegistry);
        HeatModule.init(chunkStoreRegistry, eventRegistry);
        FluidModule.init(chunkStoreRegistry, eventRegistry);
        GasModule.init(chunkStoreRegistry, eventRegistry);

        // Machines last: a machine owns neither slots nor a buffer, it reads the item and energy
        // components of the block it sits on, so both those modules have to exist first.
        MachineModule.init(chunkStoreRegistry);
    }
}
