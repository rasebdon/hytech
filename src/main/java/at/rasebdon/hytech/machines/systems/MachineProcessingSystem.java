package at.rasebdon.hytech.machines.systems;

import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.items.components.ItemBlockComponent;
import at.rasebdon.hytech.machines.MachineRecipes;
import at.rasebdon.hytech.machines.MachineSlots;
import at.rasebdon.hytech.machines.components.MachineProcessorComponent;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Runs every electric machine: pick a recipe, spend energy, hand over the results.
///
/// One system for all of them, the way [at.rasebdon.hytech.core.systems.AbstractTransferSystem] is
/// one algorithm for every resource. A crusher and an electric smelter differ only in the recipes
/// their `RecipeGroup` selects and the numbers on their processor component.
///
/// Energy is denominated **per tick**, matching how generation pays out in
/// `EnergyGenerationSystem` -- a machine that cannot afford this tick simply does not advance, and
/// nothing is consumed or lost while it waits.
public final class MachineProcessingSystem extends EntityTickingSystem<ChunkStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<ChunkStore, MachineProcessorComponent> processorType;
    private final ComponentType<ChunkStore, ItemBlockComponent> itemType;
    private final ComponentType<ChunkStore, EnergyBlockComponent> energyType;
    private final Archetype<ChunkStore> archetype;

    /// Whether a badly configured machine has already been reported. One line per server, not one
    /// per machine per tick.
    private boolean warnedAboutSlots;
    private boolean warnedAboutGroup;

    public MachineProcessingSystem(
            ComponentType<ChunkStore, MachineProcessorComponent> processorType,
            ComponentType<ChunkStore, ItemBlockComponent> itemType,
            ComponentType<ChunkStore, EnergyBlockComponent> energyType) {

        this.processorType = processorType;
        this.itemType = itemType;
        this.energyType = energyType;
        this.archetype = Archetype.of(processorType, itemType, energyType);
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        var processor = archetypeChunk.getComponent(index, this.processorType);
        var items = archetypeChunk.getComponent(index, this.itemType);
        var energy = archetypeChunk.getComponent(index, this.energyType);
        if (processor == null || items == null || energy == null) return;

        if (processor.getRecipeGroup().isEmpty()) {
            warnOnce(true, "A machine declares no RecipeGroup and can never run");
            processor.setActive(false);
            return;
        }

        var slots = MachineSlots.of(items);
        if (slots == null) {
            warnOnce(false, "A machine's hytech:items:container declares no InputSlots/OutputSlots split");
            processor.setActive(false);
            return;
        }

        var recipe = resolveRecipe(processor, slots);
        if (recipe == null) {
            processor.clearOperation();
            return;
        }

        var inputs = CraftingManager.getInputMaterials(recipe);
        var outputs = CraftingManager.getOutputItemStacks(recipe);

        // How much work is even possible this pass: ingredients on hand, room for the results, and
        // the machine's own parallelism. Established before any energy is spent.
        int sets = slots.countInputSets(inputs, processor.getParallelOperations());
        sets = slots.fittingOutputSets(outputs, sets);

        if (sets <= 0) {
            processor.setActive(false);
            return;
        }

        long cost = processor.getEnergyPerTick() * sets;
        if (cost > 0L && energy.getAmount() < cost) {
            // Held, not lost: progress stays where it is and resumes when power comes back.
            processor.setActive(false);
            return;
        }

        energy.reduce(cost);
        processor.setActive(true);
        processor.addProgress(dt);

        float operationSeconds = processor.operationSeconds(recipe.getTimeSeconds());

        if (operationSeconds > 0f) {
            if (processor.getProgress() < operationSeconds) return;

            processor.consumeProgress(operationSeconds);
        }

        slots.consumeInputs(inputs, sets);
        slots.addOutputs(outputs, sets);

        // The ingredients that were on hand are gone, so the next tick re-derives the recipe
        // rather than assuming this one still applies.
        if (slots.countInputSets(inputs, 1) <= 0) {
            processor.clearOperation();
        }
    }

    /// The recipe in flight if its ingredients are still there, otherwise the first candidate the
    /// ingredient slots satisfy.
    ///
    /// Sticking to the saved recipe matters when several would match: without it a machine could
    /// bank progress on one and pay it out on another, and the choice would flicker as the input
    /// slots change.
    @Nullable
    private CraftingRecipe resolveRecipe(MachineProcessorComponent processor, MachineSlots slots) {
        var candidates = MachineRecipes.forGroup(processor.getRecipeGroup());
        if (candidates.isEmpty()) return null;

        var current = processor.getRecipeId();

        if (current != null) {
            for (var candidate : candidates) {
                if (!current.equals(candidate.getId())) continue;

                if (slots.countInputSets(CraftingManager.getInputMaterials(candidate), 1) > 0) {
                    return candidate;
                }

                break;
            }
        }

        for (var candidate : candidates) {
            if (slots.countInputSets(CraftingManager.getInputMaterials(candidate), 1) <= 0) continue;

            // A new recipe starts from zero: progress earned on the last one is not transferable.
            processor.setRecipeId(candidate.getId());
            processor.consumeProgress(Float.MAX_VALUE);

            return candidate;
        }

        return null;
    }

    private void warnOnce(boolean aboutGroup, String message) {
        if (aboutGroup) {
            if (this.warnedAboutGroup) return;
            this.warnedAboutGroup = true;
        } else {
            if (this.warnedAboutSlots) return;
            this.warnedAboutSlots = true;
        }

        LOGGER.atWarning().log("%s", message);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return this.archetype;
    }
}
