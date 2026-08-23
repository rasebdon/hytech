package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.ui.HytechCustomPage;
import at.rasebdon.hytech.core.ui.MachinePage;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/// An energy store: charge, flow, and its transfer limit.
public class OpenBatteryPageInteraction extends OpenPageBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenBatteryPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenBatteryPageInteraction.class,
                            OpenBatteryPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .build();

    @Override
    @Nullable
    protected HytechCustomPage createPage(@NotNull World world,
                                          @NotNull Vector3i blockPos,
                                          @NotNull PlayerRef playerRef) {

        var component = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getBlockComponentType());
        if (component == null) return null;

        return new MachinePage(playerRef, world, blockPos, null, (page, view) -> {
            var energy = component.getContainer();

            view.primary("Energy",
                    String.format("%,d / %,d RF", energy.getAmount(), energy.getTotalCapacity()),
                    energy.getFillRatio(),
                    percent(energy.getFillRatio()) + "% charged");

            view.detail("Flow", signed(energy.getDelta()) + " RF/t");
            view.detail("Max transfer", energy.getTransferSpeed() + " RF/t");
        });
    }
}
