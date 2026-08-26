package at.rasebdon.hytech.machines;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Which recipes each Hytech machine may run.
///
/// Machine recipes are ordinary vanilla `CraftingRecipe` assets under
/// `Server/Item/Recipes/Hytech/`, tagged with a `BenchRequirement` whose id names the machine
/// (`Hytech_Crusher`, `Hytech_Smelter`). That buys the whole authoring format -- item or resource
/// type inputs, multiple outputs, `TimeSeconds` -- and the game's own validation, for the price of
/// this index. A Hytech bench id belongs to no block, which vanilla is content with: `CraftingPlugin`
/// creates a registry for whatever id a recipe names.
///
/// Built on first use rather than at plugin setup, because assets load on their own schedule and a
/// machine may well tick before the recipe pack is in. Rebuilt when the asset count changes, which
/// is enough to pick up a reload without listening for one.
public final class MachineRecipes {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /// Only ids under this prefix are ours. Vanilla's own benches keep their recipes to themselves.
    private static final String HYTECH_PREFIX = "Hytech_";

    private static Map<String, List<CraftingRecipe>> byGroup = Map.of();
    private static int indexedAssetCount = -1;

    private MachineRecipes() {
    }

    /// Every recipe tagged for `group`, in no particular order. Empty when the group has none --
    /// a typo in `RecipeGroup` or a missing recipe pack look the same from here, which is why the
    /// index logs what it found.
    @Nonnull
    public static List<CraftingRecipe> forGroup(@Nullable String group) {
        if (group == null || group.isEmpty()) return List.of();

        return index().getOrDefault(group, List.of());
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
                if (requirement.id == null || !requirement.id.startsWith(HYTECH_PREFIX)) continue;

                built.computeIfAbsent(requirement.id, _ -> new ArrayList<>()).add(recipe);
            }
        }

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
