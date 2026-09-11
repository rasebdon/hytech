package at.rasebdon.hytech;

import at.rasebdon.hytech.content.HytechContentModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/// Content built on HytechCore; the manifest's hard dependency guarantees the library is fully
/// initialised before this runs.
@SuppressWarnings("unused")
public final class HytechPlugin extends JavaPlugin {
    public HytechPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        HytechContentModule.init(this.getChunkStoreRegistry());
    }
}
