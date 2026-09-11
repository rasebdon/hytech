package at.rasebdon.hytech.content.generators;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;

/// Fuel is identified by vanilla's `Fuel` resource type rather than a hardcoded item list, so any
/// mod's fuel items work. Burn value comes from the item's own `FuelQuality`.
public final class FuelUtil {

    /// Resource type every burnable item declares, e.g. charcoal: `ResourceTypes: [{Id: "Fuel"}]`.
    private static final String FUEL_RESOURCE_TYPE = "Fuel";

    private FuelUtil() {
    }

    public static boolean isFuel(@Nullable ItemStack stack) {
        return fuelQuality(stack) > 0d;
    }

    /// Returns 0 unless the item both declares the Fuel resource type and has a quality > 0.
    public static double fuelQuality(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return 0d;

        var item = stack.getItem();

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

    /// Scans slots in order, removing the first fuel stack found; returns 0 without side effects
    /// if none is fuel.
    public static double consumeOne(@Nullable ItemContainer container) {
        if (container == null) return 0d;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);

            double quality = fuelQuality(stack);
            if (quality <= 0d) continue;

            var transaction = container.removeItemStackFromSlot(slot, 1);
            if (!transaction.succeeded()) continue;

            return quality;
        }

        return 0d;
    }
}
