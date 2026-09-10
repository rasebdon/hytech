package at.rasebdon.hytech;

import at.rasebdon.hytech.content.HytechContentModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/// Hytech: the content built on HytechCore.
///
/// Almost all of this plugin is assets -- pipes, batteries, tanks, generators, the crusher, the
/// smelter, twelve metals and the Tech Bench. The framework, the five resource types and the
/// machine engine come from the library, which the manifest declares as a hard dependency, so by
/// the time this runs every module in it is initialised.
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
