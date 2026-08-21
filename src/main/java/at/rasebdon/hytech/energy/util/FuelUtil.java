package at.rasebdon.hytech.energy.util;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;

/// Finding and consuming burnable items.
///
/// Fuel is identified by the vanilla `Fuel` resource type rather than by item id, so the
/// burner accepts anything the game already considers fuel -- charcoal, wood, and whatever
/// a content pack adds -- instead of a hardcoded list. Burn value comes from the item's own
/// `FuelQuality`, the same field vanilla's processing bench uses.
public final class FuelUtil {

    /// Resource type every burnable vanilla item declares. Charcoal, for instance, carries
    /// `ResourceTypes: [{ Id: "Fuel" }]` alongside `FuelQuality: 6`.
    private static final String FUEL_RESOURCE_TYPE = "Fuel";

    private FuelUtil() {
    }

    /// Whether this stack can be burnt.
    public static boolean isFuel(@Nullable ItemStack stack) {
        return fuelQuality(stack) > 0d;
    }

    /// The stack's burn value, or 0 if it is not fuel.
    ///
    /// Both conditions matter: an item could declare the resource type without a quality, or
    /// a quality without the type, and neither on its own should burn.
    public static double fuelQuality(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return 0d;

        var item = stack.getItem();
        if (item == null) return 0d;

        var resourceTypes = item.getResourceTypes();
        if (resourceTypes == null) return 0d;

        boolean declaresFuel = false;
        for (var resourceType : resourceTypes) {
            if (FUEL_RESOURCE_TYPE.equals(resourceType.id)) {
                declaresFuel = true;
                break;
            }
        }

        if (!declaresFuel) return 0d;

        return Math.max(0d, item.getFuelQuality());
    }

    /// Removes one burnable item from `container` and returns its burn value, or 0 if there
    /// was nothing to burn.
    ///
    /// Scans slots in order so a mixed container burns its first fuel stack before moving on,
    /// and only commits the removal once a fuel item is actually found.
    public static double consumeOne(@Nullable ItemContainer container) {
        if (container == null) return 0d;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);

            double quality = fuelQuality(stack);
            if (quality <= 0d) continue;

            var transaction = container.removeItemStackFromSlot(slot, 1);
            if (transaction == null || !transaction.succeeded()) continue;

            return quality;
        }

        return 0d;
    }
}
