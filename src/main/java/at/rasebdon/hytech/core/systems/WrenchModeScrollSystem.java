package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.WrenchModeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/// Lets a crouching player scroll to change which resource their wrench configures.
///
/// There is no scroll input exposed to plugins -- no packet hook, no scroll delta, and
/// `ClientFeature` has nothing for it. The only observable trace of a scroll is the hotbar's
/// active slot changing. So this watches that: if the slot moves while the player is crouching
/// and *was* holding a wrench, it treats the movement as a scroll gesture, cycles the wrench
/// mode, and puts the slot back.
///
/// Restoring the slot is what makes it feel like a modifier gesture rather than a slot change,
/// and it is also why the wrench has to be identified from the slot the player is leaving rather
/// than the one they land on.
public final class WrenchModeScrollSystem extends TickingSystem<EntityStore> {

    private static final String WRENCH_ITEM_ID = "Wrench";

    /// Every tick. A scroll is a single discrete event, and sampling slower would drop gestures
    /// or, worse, see the slot settle somewhere else and cycle by the wrong amount.
    private final ComponentType<EntityStore, WrenchModeComponent> modeType;

    /// Last seen active slot per player, keyed by entity index because `Ref` has no equals.
    private final Map<Integer, Byte> lastSlot = new HashMap<>();

    public WrenchModeScrollSystem(ComponentType<EntityStore, WrenchModeComponent> modeType) {
        this.modeType = modeType;
    }

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<EntityStore> store) {
        var pending = new java.util.ArrayList<Runnable>();
        var seen = new java.util.HashSet<Integer>();

        store.forEachChunk(Player.getComponentType(), (chunk, _) -> {
            for (int i = 0; i < chunk.size(); i++) {
                seen.add(updatePlayer(store, chunk, i, pending));
            }
        });

        // Players who left keep no state, so a rejoin does not look like a scroll.
        for (var key : Set.copyOf(this.lastSlot.keySet())) {
            if (!seen.contains(key)) {
                this.lastSlot.remove(key);
            }
        }

        if (pending.isEmpty()) return;

        store.getExternalData().getWorld().execute(() -> pending.forEach(Runnable::run));
    }

    private int updatePlayer(
            Store<EntityStore> store,
            ArchetypeChunk<EntityStore> chunk,
            int index,
            java.util.List<Runnable> pending) {

        var playerRef = chunk.getReferenceTo(index);
        int key = playerRef.getIndex();

        var hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return key;

        byte slot = hotbar.getActiveSlot();
        Byte previous = this.lastSlot.put(key, slot);

        if (previous == null || previous == slot) return key;

        // The slot moved. Only a crouching player holding a wrench meant it as a gesture.
        if (!isCrouching(store, playerRef)) return key;
        if (!wasHoldingWrench(hotbar, previous)) return key;

        int direction = scrollDirection(previous, slot, hotbar.getInventory().getCapacity());
        if (direction == 0) return key;

        // Put the slot back so the player keeps holding the wrench, and remember that as the
        // current slot -- otherwise the restore itself reads as another scroll next tick.
        this.lastSlot.put(key, previous);

        pending.add(() -> {
            if (!playerRef.isValid()) return;

            hotbar.setActiveSlot(previous, playerRef, store);
            cycleMode(store, playerRef, direction);
        });

        return key;
    }

    private boolean isCrouching(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        var movement = store.getComponent(playerRef, MovementStatesComponent.getComponentType());
        if (movement == null) return false;

        var states = movement.getMovementStates();

        return states != null && (states.crouching || states.forcedCrouching);
    }

    private boolean wasHoldingWrench(InventoryComponent.Hotbar hotbar, byte slot) {
        var inventory = hotbar.getInventory();
        if (slot < 0 || slot >= inventory.getCapacity()) return false;

        var stack = inventory.getItemStack(slot);

        return !ItemStack.isEmpty(stack) && WRENCH_ITEM_ID.equals(stack.getItemId());
    }

    /// Which way the slot moved, treating the hotbar as a ring so the wrap at either end still
    /// reads as one step rather than a jump across the whole bar.
    private int scrollDirection(byte from, byte to, int capacity) {
        if (capacity <= 1) return 0;

        int forward = Math.floorMod(to - from, capacity);
        int backward = Math.floorMod(from - to, capacity);

        if (forward == backward) return 0;

        return forward < backward ? 1 : -1;
    }

    private void cycleMode(Store<EntityStore> store, Ref<EntityStore> playerRef, int direction) {
        var mode = store.getComponent(playerRef, this.modeType);
        if (mode == null) {
            mode = new WrenchModeComponent();
            store.putComponent(playerRef, this.modeType, mode);
        }

        var selected = mode.cycle(direction);
        if (selected == null) return;

        HytechUtil.sendPlayerMessage(playerRef, "Wrench mode: " + selected.label());
    }
}
