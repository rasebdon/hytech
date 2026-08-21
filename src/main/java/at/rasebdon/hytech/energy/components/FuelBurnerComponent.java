package at.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/// Burn state for a generator that consumes solid fuel.
///
/// Kept separate from [EnergyGeneratorComponent] so solar and wind generators do not carry
/// three fields they never use, and so a block declares its burner-ness by attaching this
/// rather than by a flag.
///
/// The fuel *items* live in the block's `hytech:items:container`, not here -- which is what
/// lets an item pipe feed the burner automatically.
public class FuelBurnerComponent implements Component<ChunkStore> {

    /// How long one point of an item's `FuelQuality` burns for. Vanilla charcoal is quality
    /// 6, so the default gives it 12 seconds -- close to a vanilla furnace's feel.
    private static final float DEFAULT_SECONDS_PER_QUALITY = 2f;

    @Nonnull
    public static final BuilderCodec<FuelBurnerComponent> CODEC =
            BuilderCodec.builder(FuelBurnerComponent.class, FuelBurnerComponent::new)
                    .append(new KeyedCodec<>("SecondsPerFuelQuality", Codec.FLOAT),
                            (c, v) -> c.secondsPerQuality = v,
                            (c) -> c.secondsPerQuality)
                    .addValidator(Validators.greaterThan(0f))
                    .documentation("Seconds of burn time granted per point of item FuelQuality")
                    .add()
                    .append(new KeyedCodec<>("BurnTimeRemaining", Codec.FLOAT),
                            (c, v) -> c.burnTimeRemaining = v,
                            (c) -> c.burnTimeRemaining)
                    .addValidator(Validators.greaterThanOrEqual(0f))
                    .documentation("Seconds of burn left on the item currently alight")
                    .add()
                    .append(new KeyedCodec<>("CurrentFuelBurnTime", Codec.FLOAT),
                            (c, v) -> c.currentFuelBurnTime = v,
                            (c) -> c.currentFuelBurnTime)
                    .addValidator(Validators.greaterThanOrEqual(0f))
                    .documentation("Full burn duration of the item currently alight, for the UI progress bar")
                    .add()
                    .build();

    private float secondsPerQuality;
    private float burnTimeRemaining;
    private float currentFuelBurnTime;

    public FuelBurnerComponent() {
        this(DEFAULT_SECONDS_PER_QUALITY, 0f, 0f);
    }

    public FuelBurnerComponent(float secondsPerQuality, float burnTimeRemaining, float currentFuelBurnTime) {
        this.secondsPerQuality = secondsPerQuality <= 0f ? DEFAULT_SECONDS_PER_QUALITY : secondsPerQuality;
        this.burnTimeRemaining = Math.max(0f, burnTimeRemaining);
        this.currentFuelBurnTime = Math.max(0f, currentFuelBurnTime);
    }

    public boolean isBurning() {
        return this.burnTimeRemaining > 0f;
    }

    /// Fraction of the current item's burn left, for the UI. Zero when nothing is alight.
    public float getBurnRatio() {
        if (this.currentFuelBurnTime <= 0f) return 0f;

        return Math.clamp(this.burnTimeRemaining / this.currentFuelBurnTime, 0f, 1f);
    }

    public float getBurnTimeRemaining() {
        return this.burnTimeRemaining;
    }

    /// Lights a fresh item of the given fuel quality.
    public void ignite(double fuelQuality) {
        float duration = (float) (fuelQuality * this.secondsPerQuality);
        if (duration <= 0f) return;

        this.currentFuelBurnTime = duration;
        this.burnTimeRemaining = duration;
    }

    /// Burns for `dt` seconds. Returns the seconds actually burnt, which is less than `dt`
    /// on the tick the fuel runs out -- so a partial tick generates a partial amount rather
    /// than a full one.
    public float consume(float dt) {
        if (dt <= 0f || !isBurning()) return 0f;

        float burnt = Math.min(dt, this.burnTimeRemaining);
        this.burnTimeRemaining -= burnt;

        if (this.burnTimeRemaining <= 0f) {
            this.burnTimeRemaining = 0f;
            this.currentFuelBurnTime = 0f;
        }

        return burnt;
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new FuelBurnerComponent(this.secondsPerQuality, this.burnTimeRemaining, this.currentFuelBurnTime);
    }

    @Override
    public String toString() {
        return isBurning()
                ? String.format("Burning: %.1fs left (%.0f%%)", this.burnTimeRemaining, this.getBurnRatio() * 100f)
                : "Not burning";
    }
}
