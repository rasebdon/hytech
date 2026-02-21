package at.rasebdon.hytech;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.items.ItemModule;
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
        EnergyModule.init(chunkStoreRegistry, eventRegistry);
        ItemModule.init(chunkStoreRegistry, eventRegistry);
    }
}
