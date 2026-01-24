package com.rasebdon.hytech;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.rasebdon.hytech.energy.EnergyModule;

// Thanks to notnotnotswipez for supporting on the official Hytale Discord

@SuppressWarnings("unused")
public final class HytechPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HytechPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        EnergyModule.init(this.getChunkStoreRegistry(), this.getEventRegistry());
    }
}
