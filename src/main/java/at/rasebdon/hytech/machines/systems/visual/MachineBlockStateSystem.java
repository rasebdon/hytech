package at.rasebdon.hytech.machines.systems.visual;

import at.rasebdon.hytech.core.systems.AbstractBlockStateSystem;
import at.rasebdon.hytech.machines.components.MachineProcessorComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/// Lights a machine while it is actually working.
///
/// Both states are named, as they are for the burner: a block state cannot be cleared, so stopping
/// means switching to `Idle` rather than unsetting `Processing`. The names match vanilla's bench
/// states, so the texture-swap idiom in our assets reads the same as in the game's own.
public final class MachineBlockStateSystem extends AbstractBlockStateSystem<MachineProcessorComponent> {

    private static final String STATE_PROCESSING = "Processing";
    private static final String STATE_IDLE = "Idle";

    private static final float UPDATE_INTERVAL_SECONDS = 0.5f;

    public MachineBlockStateSystem(ComponentType<ChunkStore, MachineProcessorComponent> componentType) {
        super(componentType, UPDATE_INTERVAL_SECONDS);
    }

    @Override
    protected @NotNull String resolveState(@NonNull MachineProcessorComponent component) {
        return component.isActive() ? STATE_PROCESSING : STATE_IDLE;
    }
}
