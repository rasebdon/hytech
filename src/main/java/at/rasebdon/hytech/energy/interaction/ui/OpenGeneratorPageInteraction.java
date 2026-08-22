package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.HytechPage;
import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.interactions.ui.UiItemTransfer;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.components.FuelBurnerComponent;
import at.rasebdon.hytech.energy.util.FuelUtil;
import at.rasebdon.hytech.items.ItemModule;
import au.ellie.hyui.builders.ItemGridBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.PageRefreshResult;
import au.ellie.hyui.events.SlotClickingEventData;
import au.ellie.hyui.events.UIContext;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/// Opens the page matching the block's generator type.
public class OpenGeneratorPageInteraction extends OpenPageBlockInteraction {

    private static final String FUEL_GRID_ID = "fuel-grid";
    private static final String FUEL_WINDOW_ID = "fuel-window-button";

    /// Wind output ramps between these heights, matching `EnergyGenerationSystem`.
    private static final int WIND_MIN_HEIGHT = 64;
    private static final int WIND_MAX_HEIGHT = 160;

    @Nonnull
    public static final BuilderCodec<OpenGeneratorPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenGeneratorPageInteraction.class,
                            OpenGeneratorPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .build();

    @Override
    @Nullable
    protected PageBuilder getPageBuilder(@NotNull InteractionContext context,
                                         @NotNull World world,
                                         @NotNull Vector3i blockPos) {

        var generator = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getGeneratorComponentType());
        if (generator == null) return null;

        var containerComponent = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getBlockComponentType());
        if (containerComponent == null || !containerComponent.isAvailable()) return null;

        var energy = containerComponent.getContainer();

        var template = new TemplateProcessor()
                .setVariable("blockName", getBlockName(world, blockPos))
                .setVariable("currentRate", generator::getCurrentRate)
                .setVariable("maxRate", generator::getBaseRate)
                .setVariable("ratePrefix", () -> getPrefix(generator.getCurrentRate()))
                .setVariable("rateColor", () -> getValueColor(generator.getCurrentRate()))
                .setVariable("currentEnergy", energy::getAmount)
                .setVariable("maxEnergy", energy::getTotalCapacity)
                .setVariable("energyFillRatio", energy::getFillRatio);

        return switch (generator.getGeneratorType()) {
            case SOLAR -> solarPage(world, template);
            case WIND -> windPage(blockPos, template);
            case FUEL_SOLID -> burnerPage(context, world, blockPos, template);
            // No fluid module yet, so there is nothing meaningful to show.
            case FUEL_LIQUID -> null;
        };
    }

    private PageBuilder solarPage(@NotNull World world, TemplateProcessor template) {
        template
                .setVariable("efficiency", () -> sunlight(world))
                .setVariable("efficiencyPercent", () -> Math.round(sunlight(world) * 100d));

        return HytechPage.of("Energy/Generators/SolarPanelPage.html", template);
    }

    /// Double rather than float because that is what `WorldTimeResource` reports.
    private double sunlight(@NotNull World world) {
        var time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());

        return time == null ? 0d : time.getSunlightFactor();
    }

    private PageBuilder windPage(@NotNull Vector3i blockPos, TemplateProcessor template) {
        float ramp = (blockPos.y - WIND_MIN_HEIGHT) / (float) (WIND_MAX_HEIGHT - WIND_MIN_HEIGHT);
        float factor = Math.max(0f, Math.min(1f, ramp));

        template
                .setVariable("altitude", blockPos.y)
                .setVariable("altitudeFactor", factor);

        return HytechPage.of("Energy/Generators/WindTurbinePage.html", template);
    }

    /// The burner page: fuel slots the player can drag into, plus a burn readout.
    private PageBuilder burnerPage(@NotNull InteractionContext context,
                                   @NotNull World world,
                                   @NotNull Vector3i blockPos,
                                   TemplateProcessor template) {

        var burner = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getFuelBurnerComponentType());
        if (burner == null) return null;

        var fuelComponent = HytechUtil.getBlockComponent(
                world, blockPos, ItemModule.get().getBlockComponentType());
        if (fuelComponent == null) return null;

        var fuel = fuelComponent.getItemContainer();
        if (fuel == null) return null;

        template
                .setVariable("burnRatio", burner::getBurnRatio)
                .setVariable("burnStatus", () -> burnStatus(burner))
                .setVariable("burnColor", () -> burner.isBurning() ? "#ffa52b" : "#9a9a9a");

        var playerRef = context.getEntity();
        var store = world.getEntityStore().getStore();

        var page = HytechPage.of("Energy/Generators/BurnerGeneratorPage.html", template);

        // The grid is declared in the HTML, as HyUI's own examples do it. Filling that element is
        // the point: building a second one with addElement registered the id but left the element
        // outside the container's layout tree, so no slots ever appeared.
        fillGrid(page, fuel);

        return page
                // Hands the fuel container to a real inventory window -- the same mechanism a
                // chest or an alchemy table uses, so drag and drop is engine-handled and the
                // player's own inventory is on screen. A HyUI page is an overlay and never shows
                // the inventory, so no amount of grid configuration could have made dragging work.
                .addEventListener(FUEL_WINDOW_ID, CustomUIEventBindingType.Activating,
                        // No explicit close: setPageWithWindows replaces whatever page is open,
                        // and closing first cancelled the window before it could be shown.
                        (_, _) -> world.execute(() -> openFuelWindow(world, playerRef, fuel)))
                // Clicking a slot takes it back out, so fuel is never trapped in the machine.
                .addEventListener(FUEL_GRID_ID, CustomUIEventBindingType.SlotClicking,
                        SlotClickingEventData.class, (event, ctx) -> {
                            var slot = event.getSlotIndex();
                            if (slot == null) return;

                            UiItemTransfer.toPlayer(store, playerRef, fuel,
                                    slot.shortValue(), Integer.MAX_VALUE);
                            refreshGrid(ctx, fuel);
                        })
                .onRefresh(openPage -> {
                    refreshGrid(openPage, fuel);
                    return PageRefreshResult.UPDATE;
                });
    }

    /// Opens the fuel container as a vanilla inventory window.
    ///
    /// `Page.Bench` with a `ContainerWindow` is exactly what `OpenContainerInteraction` does for a
    /// chest, so the player gets their own inventory alongside and the engine performs the item
    /// moves. Nothing here validates the items: the same container is fed by item pipes, and
    /// rejecting fuel by hand while accepting it by pipe would be incoherent.
    private void openFuelWindow(World world, Ref<EntityStore> playerRef, ItemContainer fuel) {
        var store = world.getEntityStore().getStore();

        var player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;

        var pageManager = player.getPageManager();
        if (pageManager == null) return;

        pageManager.setPageWithWindows(playerRef, store, Page.Bench, true, new ContainerWindow(fuel));
    }

    private String burnStatus(FuelBurnerComponent burner) {
        if (!burner.isBurning()) return "No fuel";

        return String.format("Burning - %.0fs left", Math.ceil(burner.getBurnTimeRemaining()));
    }

    /// Fills the HTML-declared grid before the page opens.
    private void fillGrid(PageBuilder page, ItemContainer fuel) {
        page.editById(FUEL_GRID_ID, ItemGridBuilder.class, grid -> grid.withSlots(slotsOf(fuel)));
    }

    /// Refreshes it afterwards, from an event handler or the refresh tick.
    private void refreshGrid(UIContext ctx, ItemContainer fuel) {
        ctx.editById(FUEL_GRID_ID, ItemGridBuilder.class, grid -> grid.withSlots(slotsOf(fuel)));
    }

    /// Snapshot of the container as grid slots.
    ///
    /// Rebuilt wholesale each refresh rather than diffed: the container is a handful of slots
    /// and an item pipe can change any of them between refreshes, so tracking which moved
    /// would cost more than it saves.
    private List<ItemGridSlot> slotsOf(ItemContainer fuel) {
        var slots = new ArrayList<ItemGridSlot>(fuel.getCapacity());

        for (short slot = 0; slot < fuel.getCapacity(); slot++) {
            var stack = fuel.getItemStack(slot);

            // Marking non-fuel as incompatible tells the player why nothing is burning.
            slots.add(ItemStack.isEmpty(stack)
                    ? new ItemGridSlot()
                    : new ItemGridSlot(stack).setItemIncompatible(!FuelUtil.isFuel(stack)));
        }

        return slots;
    }
}
