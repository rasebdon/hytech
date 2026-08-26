package at.rasebdon.hytech.machines.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.ui.HytechCustomPage;
import at.rasebdon.hytech.core.ui.MachinePage;
import at.rasebdon.hytech.core.ui.MachineView;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.items.ItemModule;
import at.rasebdon.hytech.items.components.ItemBlockComponent;
import at.rasebdon.hytech.machines.MachineModule;
import at.rasebdon.hytech.machines.MachineRecipes;
import at.rasebdon.hytech.machines.components.MachineProcessorComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/// The page for an electric machine: its charge, what it is making, and its slots.
///
/// One interaction for every machine, because a crusher and a smelter differ only in the numbers
/// the same three components hold -- exactly as `OpenGeneratorPage` covers all three generators.
public class OpenMachinePageInteraction extends OpenPageBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenMachinePageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenMachinePageInteraction.class,
                            OpenMachinePageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .build();

    @Override
    @Nullable
    protected HytechCustomPage createPage(@NotNull World world,
                                          @NotNull Vector3i blockPos,
                                          @NotNull PlayerRef playerRef) {

        var processor = HytechUtil.getBlockComponent(
                world, blockPos, MachineModule.get().getProcessorComponentType());
        if (processor == null) return null;

        var energy = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getBlockComponentType());
        if (energy == null) return null;

        var items = HytechUtil.getBlockComponent(
                world, blockPos, ItemModule.get().getBlockComponentType());

        // Non-null container is what gives the page its Slots button and the player's inventory
        // alongside; a machine with no item component would simply not offer one.
        var container = items == null ? null : items.getItemContainer();

        return new MachinePage(playerRef, world, blockPos, container,
                (page, view) -> fill(view, processor, energy.getContainer(), items));
    }

    private void fill(MachineView view,
                      MachineProcessorComponent processor,
                      HytechEnergyContainer energy,
                      @Nullable ItemBlockComponent items) {

        long draw = processor.isActive() ? processor.getEnergyPerTick() * processor.getParallelOperations() : 0L;

        view.primary("Energy",
                String.format("%,d / %,d RF", energy.getAmount(), energy.getTotalCapacity()),
                energy.getFillRatio(),
                signed(-draw) + " RF/t");

        view.secondary("Progress", progressRatio(processor), status(processor, energy));

        if (items != null) {
            view.container("Slots", items.getItemContainer(), null);
        }

        view.detail("Recipes", MachineRecipes.forGroup(processor.getRecipeGroup()).size()
                + " known (" + processor.getRecipeGroup() + ")");
        view.detail("Speed", String.format("%.2fx", processor.getSpeedMultiplier()));
        view.detail("Parallel", processor.getParallelOperations() + " per operation");
    }

    /// Progress against the operation in flight, read from the recipe the machine is running.
    ///
    /// Zero when nothing is running: there is no operation to measure against, and a stale bar
    /// reads as a stuck machine.
    private static float progressRatio(MachineProcessorComponent processor) {
        var recipeId = processor.getRecipeId();
        if (recipeId == null) return 0f;

        var recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) return 0f;

        float operation = processor.operationSeconds(recipe.getTimeSeconds());

        // An instant recipe never sits at a fraction, so show it as ready rather than empty.
        return operation <= 0f ? 1f : processor.getProgress() / operation;
    }

    private static String status(MachineProcessorComponent processor, HytechEnergyContainer energy) {
        if (processor.isActive()) return "Working";
        if (processor.getRecipeId() != null && energy.isEmpty()) return "No power";
        if (processor.getRecipeId() != null) return "Blocked";

        return "Idle";
    }
}
