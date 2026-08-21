package at.rasebdon.hytech.energy.systems.visual;

import at.rasebdon.hytech.core.systems.AbstractBlockStateSystem;
import at.rasebdon.hytech.energy.components.FuelBurnerComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;

/// Lights a burner's firebox while it has fuel alight.
///
/// Both states are named rather than only the lit one, because a block state cannot be
/// cleared -- so going out means switching to `Idle`, not unsetting `Burning`.
public final class BurnerBlockStateSystem extends AbstractBlockStateSystem<FuelBurnerComponent> {

    private static final String STATE_BURNING = "Burning";
    private static final String STATE_IDLE = "Idle";

    /// Twice a second: fast enough that lighting and going out feel immediate, and the write
    /// is skipped entirely when the state has not actually changed.
    private static final float UPDATE_INTERVAL_SECONDS = 0.5f;

    public BurnerBlockStateSystem(ComponentType<ChunkStore, FuelBurnerComponent> componentType) {
        super(componentType, UPDATE_INTERVAL_SECONDS);
    }

    @Override
    @Nullable
    protected String resolveState(@NonNull FuelBurnerComponent component) {
        return component.isBurning() ? STATE_BURNING : STATE_IDLE;
    }
}
