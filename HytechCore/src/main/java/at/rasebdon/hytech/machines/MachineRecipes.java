package at.rasebdon.hytech.machines;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/// Which recipes each machine may run: ordinary vanilla `CraftingRecipe` assets tagged with a
/// `Processing` `BenchRequirement` naming the machine group, indexed here with no registration call
/// needed -- any mod's machine is found the same way, which is what lets a content mod add one in
/// assets alone. Built lazily and rebuilt on asset-count change, since a machine may tick before the
/// recipe pack loads.
public final class MachineRecipes {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();


    private static Map<String, List<CraftingRecipe>> byGroup = Map.of();
    private static int indexedAssetCount = -1;

    private MachineRecipes() {
    }

    @Nonnull
    public static List<CraftingRecipe> forGroup(@Nullable String group) {
        if (group == null || group.isEmpty()) return List.of();

        return index().getOrDefault(group, List.of());
    }

    /// Deliberately non-positional, unlike vanilla's `CraftingManager.matchesAnyRecipe`: a machine's
    /// input slots are interchangeable ([MachineSlots#countInputSets]), so matching by slot position
    /// would refuse loads the machine would happily process.
    public static boolean acceptsIngredient(@Nullable String group, @Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return false;

        for (var recipe : forGroup(group)) {
            var inputs = recipe.getInput();
            if (inputs == null) continue;

            for (var input : inputs) {
                if (CraftingManager.matches(input, stack)) return true;
            }
        }

        return false;
    }

    @Nonnull
    public static synchronized Map<String, List<CraftingRecipe>> index() {
        var assets = CraftingRecipe.getAssetMap().getAssetMap();

        if (assets.size() == indexedAssetCount) return byGroup;

        var built = new HashMap<String, List<CraftingRecipe>>();

        for (var recipe : assets.values()) {
            var requirements = recipe.getBenchRequirement();
            if (requirements == null) continue;

            for (var requirement : requirements) {
                // Keyed on requirement type, not an id prefix, so any mod's machines are found --
                // not just ones prefixed Hytech_.
                if (requirement.type != BenchType.Processing || requirement.id.isEmpty()) continue;

                built.computeIfAbsent(requirement.id, _ -> new ArrayList<>()).add(recipe);
            }
        }

        // Most ingredients first, so the more specific recipe wins when several match (e.g. an
        // alloy over its base metal alone).
        built.values().forEach(recipes -> recipes.sort(
                Comparator.comparingInt((CraftingRecipe recipe) ->
                        recipe.getInput() == null ? 0 : recipe.getInput().length).reversed()));

        byGroup = Map.copyOf(built);
        indexedAssetCount = assets.size();

        LOGGER.atInfo().log("Indexed Hytech machine recipes: %s",
                built.isEmpty() ? "none" : summarise(built));

        return byGroup;
    }

    private static String summarise(Map<String, List<CraftingRecipe>> built) {
        var out = new StringBuilder();

        built.forEach((group, recipes) -> {
            if (!out.isEmpty()) out.append(", ");
            out.append(group).append('=').append(recipes.size());
        });

        return out.toString();
    }
}
