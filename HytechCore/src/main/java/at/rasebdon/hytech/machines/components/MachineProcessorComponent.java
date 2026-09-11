package at.rasebdon.hytech.machines.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Everything specific to a machine *kind* lives in its asset, not in code -- a crusher and an
/// electric smelter are the same component with different numbers. Items live in the block's
/// `hytech:items:container`, so item pipes feed and drain it with no transfer code of its own.
public class MachineProcessorComponent implements Component<ChunkStore> {

    public static final String UNSET_GROUP = "";

    @Nonnull
    public static final BuilderCodec<MachineProcessorComponent> CODEC =
            BuilderCodec.builder(MachineProcessorComponent.class, MachineProcessorComponent::new)
                    .append(new KeyedCodec<>("RecipeGroup", Codec.STRING),
                            (c, v) -> c.recipeGroup = v,
                            (c) -> c.recipeGroup)
                    .documentation("Bench id the machine's recipes are tagged with, e.g. Hytech_Crusher")
                    .add()
                    .append(new KeyedCodec<>("EnergyPerTick", Codec.LONG),
                            (c, v) -> c.energyPerTick = v,
                            (c) -> c.energyPerTick)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Energy drawn per tick per running operation")
                    .add()
                    .append(new KeyedCodec<>("SpeedMultiplier", Codec.FLOAT),
                            (c, v) -> c.speedMultiplier = v,
                            (c) -> c.speedMultiplier)
                    .addValidator(Validators.greaterThan(0f))
                    .documentation("Divides a recipe's TimeSeconds; 2 means twice as fast")
                    .add()
                    .append(new KeyedCodec<>("ParallelOperations", Codec.INTEGER),
                            (c, v) -> c.parallelOperations = v,
                            (c) -> c.parallelOperations)
                    .addValidator(Validators.greaterThanOrEqual(1))
                    .documentation("How many operations complete together, for factory tiers")
                    .add()
                    .append(new KeyedCodec<>("Progress", Codec.FLOAT),
                            (c, v) -> c.progress = v,
                            (c) -> c.progress)
                    .addValidator(Validators.greaterThanOrEqual(0f))
                    .documentation("Seconds of progress on the operation in flight")
                    .add()
                    .append(new KeyedCodec<>("RecipeId", Codec.STRING),
                            (c, v) -> c.recipeId = v,
                            (c) -> c.recipeId)
                    .documentation("Recipe currently in progress, so a reload resumes it rather than restarting")
                    .add()
                    .build();

    private String recipeGroup;
    private long energyPerTick;
    private float speedMultiplier;
    private int parallelOperations;

    private float progress;

    @Nullable
    private String recipeId;

    // Not persisted: a freshly loaded machine reads as idle for one tick rather than saving a lie.
    private transient boolean active;

    public MachineProcessorComponent() {
        this(UNSET_GROUP, 0L, 1f, 1, 0f, null);
    }

    public MachineProcessorComponent(
            String recipeGroup,
            long energyPerTick,
            float speedMultiplier,
            int parallelOperations,
            float progress,
            @Nullable String recipeId) {

        this.recipeGroup = recipeGroup == null ? UNSET_GROUP : recipeGroup;
        this.energyPerTick = Math.max(0L, energyPerTick);
        this.speedMultiplier = speedMultiplier <= 0f ? 1f : speedMultiplier;
        this.parallelOperations = Math.max(1, parallelOperations);
        this.progress = Math.max(0f, progress);
        this.recipeId = recipeId;
    }

    public String getRecipeGroup() {
        return this.recipeGroup;
    }

    public long getEnergyPerTick() {
        return this.energyPerTick;
    }

    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    public int getParallelOperations() {
        return this.parallelOperations;
    }

    public float getProgress() {
        return this.progress;
    }

    public void addProgress(float seconds) {
        this.progress = Math.max(0f, this.progress + seconds);
    }

    public void consumeProgress(float seconds) {
        this.progress = Math.max(0f, this.progress - seconds);
    }

    @Nullable
    public String getRecipeId() {
        return this.recipeId;
    }

    public void setRecipeId(@Nullable String recipeId) {
        this.recipeId = recipeId;
    }

    public void clearOperation() {
        this.recipeId = null;
        this.progress = 0f;
        this.active = false;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public float operationSeconds(float recipeTimeSeconds) {
        if (recipeTimeSeconds <= 0f) return 0f;

        return recipeTimeSeconds / this.speedMultiplier;
    }

    // Copy-constructed, not super.clone()'d: Object.clone is a shallow copy that would share
    // mutable state with the original.
    @SuppressWarnings({"CloneDoesntCallSuperClone", "MethodDoesntCallSuperMethod"})
    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new MachineProcessorComponent(this.recipeGroup, this.energyPerTick,
                this.speedMultiplier, this.parallelOperations, this.progress, this.recipeId);
    }

    @Override
    public String toString() {
        if (this.recipeId == null) return String.format("Idle (%s)", this.recipeGroup);

        return String.format("%s: %s at %.1fs", this.recipeGroup, this.recipeId, this.progress);
    }
}
