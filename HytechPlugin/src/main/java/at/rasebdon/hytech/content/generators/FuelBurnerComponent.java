package at.rasebdon.hytech.content.generators;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/// Separate from [EnergyGeneratorComponent] so solar/wind carry none of these fields, and so
/// burner-ness is attaching this component rather than a flag. Fuel items live in
/// `hytech:items:container`, not here, so pipes can feed the burner directly.
public class FuelBurnerComponent implements Component<ChunkStore> {

    /// Seconds burned per point of `FuelQuality`; charcoal (quality 6) gets ~12s, close to a
    /// vanilla furnace.
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

    public float getBurnRatio() {
        if (this.currentFuelBurnTime <= 0f) return 0f;

        return Math.clamp(this.burnTimeRemaining / this.currentFuelBurnTime, 0f, 1f);
    }

    public float getBurnTimeRemaining() {
        return this.burnTimeRemaining;
    }

    public void ignite(double fuelQuality) {
        float duration = (float) (fuelQuality * this.secondsPerQuality);
        if (duration <= 0f) return;

        this.currentFuelBurnTime = duration;
        this.burnTimeRemaining = duration;
    }

    /// Returns seconds actually burnt, which is less than `dt` on the tick fuel runs out.
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

    /// Copy-constructed, not `super.clone()`d: `Object.clone` is a shallow copy, which would share
    /// mutable state with the original.
    @SuppressWarnings({"CloneDoesntCallSuperClone", "MethodDoesntCallSuperMethod"})
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
