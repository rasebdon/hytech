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

    /// How long the operation in flight has left, in seconds. Zero when nothing is running.
    private static float secondsRemaining(MachineProcessorComponent processor) {
        float total = operationSeconds(processor);

        return total <= 0f ? 0f : Math.max(0f, total - processor.getProgress());
    }

    /// The length of the current operation, or zero when there is none.
    private static float operationSeconds(MachineProcessorComponent processor) {
        var recipeId = processor.getRecipeId();
        if (recipeId == null) return 0f;

        var recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) return 0f;

        return processor.operationSeconds(recipe.getTimeSeconds());
    }

    /// Progress against the operation in flight, read from the recipe the machine is running.
    ///
    /// Zero when nothing is running: there is no operation to measure against, and a stale bar
    /// reads as a stuck machine.
    private static float progressRatio(MachineProcessorComponent processor) {
        if (processor.getRecipeId() == null) return 0f;

        float operation = operationSeconds(processor);

        // An instant recipe never sits at a fraction, so show it as ready rather than empty.
        return operation <= 0f ? 1f : processor.getProgress() / operation;
    }

    private void fill(MachineView view,
                      MachineProcessorComponent processor,
                      HytechEnergyContainer energy,
                      @Nullable ItemBlockComponent items) {

        long draw = processor.isActive() ? processor.getEnergyPerTick() * processor.getParallelOperations() : 0L;

        view.primary(
                String.format("%,d / %,d RF", energy.getAmount(), energy.getTotalCapacity()),
                energy.getFillRatio(),
                signed(-draw) + " RF/t");

        String status = status(processor, energy);

        // The split is the machine's own: the leading slots take ingredients, the trailing ones
        // hold results, and `MachineSlots` is the only other place that arithmetic lives. Drawing
        // it means the page shows a crusher the way a crusher works -- ore on the left of the
        // arrow, dust on the right -- rather than one undifferentiated row.
        if (items != null) {
            // The predicate does double duty: it greys the contents summary when the machine is
            // loaded with something it cannot use, and it is what the page checks before letting a
            // click-transfer drop an item into an ingredient slot. Non-positional on purpose --
            // MachineSlots matches across the whole input range, so the UI must not be stricter
            // about which slot a dust goes in than the machine itself is.
            var group = processor.getRecipeGroup();

            view.slots("Processing", items.getItemContainer(),
                    items.getInputSlots(), items.getOutputSlots(),
                    stack -> !MachineRecipes.acceptsIngredient(group, stack));

            view.progress(progressRatio(processor), secondsRemaining(processor), status);
        }

        view.detail("Recipes", MachineRecipes.forGroup(processor.getRecipeGroup()).size()
                + " known (" + processor.getRecipeGroup() + ")");
        view.detail("Speed", String.format("%.2fx", processor.getSpeedMultiplier()));
        view.detail("Parallel", processor.getParallelOperations() + " per operation");
    }

    private static String status(MachineProcessorComponent processor, HytechEnergyContainer energy) {
        if (processor.isActive()) return "Working";
        if (processor.getRecipeId() != null && energy.isEmpty()) return "No power";
        if (processor.getRecipeId() != null) return "Blocked";

        return "Idle";
    }
}
