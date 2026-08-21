package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigState;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.EventBusUtil;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.Collectors;


public abstract class LogisticComponent<TContainer>
        extends ContainerHolder<TContainer>
        implements Component<ChunkStore> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticComponent.class)
                    .append(new KeyedCodec<>("BlockFaceConfig", BlockFaceConfig.CODEC),
                            (c, v) -> c.blockFaceConfig = v,
                            (c) -> c.blockFaceConfig)
                    .documentation("Side configuration for Logistic Container Block").add()
                    .build();

    protected BlockFaceConfig blockFaceConfig;

    protected LogisticComponent(BlockFaceConfig blockFaceConfig) {
        this();
        this.blockFaceConfig = blockFaceConfig.clone();
    }

    protected LogisticComponent() {
        super();
        this.blockFaceConfig = new BlockFaceConfig();
    }

    public BlockFaceConfigType getFaceConfigTowards(ContainerHolder<TContainer> holder) {
        return this.getFaceConfigTowards(getNeighborFace(holder));
    }

    public BlockFaceConfigType getFaceConfigTowards(BlockFace face) {
        return this.blockFaceConfig.getType(face);
    }

    public boolean hasInputOrBothTowards(ContainerHolder<TContainer> holder) {
        return this.blockFaceConfig.isInputOrBoth(getNeighborFace(holder));
    }

    public boolean hasOutputOrBothTowards(ContainerHolder<TContainer> holder) {
        return this.blockFaceConfig.isOutputOrBoth(getNeighborFace(holder));
    }

    public boolean hasInputTowards(ContainerHolder<TContainer> target) {
        return getFaceConfigTowards(target) == BlockFaceConfigType.INPUT;
    }

    public boolean hasOutputTowards(ContainerHolder<TContainer> target) {
        return getFaceConfigTowards(target) == BlockFaceConfigType.OUTPUT;
    }

    public void cycleBlockFaceConfig(BlockFace face) {
        if (isPipeToPipe(face)) {
            // Direction is meaningless between two pipes -- they are the same network, so a
            // pipe-to-pipe face is only ever connected or not.
            blockFaceConfig.toggleFace(face);
        } else {
            blockFaceConfig.cycleFace(face);
        }

        this.reload();
        this.reloadNeighborHolder(face);
    }

    /// True when the neighbour on this face is another pipe rather than a container.
    private boolean isPipeToPipe(BlockFace face) {
        var neighbor = getNeighbor(face);
        return neighbor != null && neighbor.getHolder() instanceof LogisticPipeComponent<?>;
    }

    private void reloadNeighborHolder(BlockFace face) {
        var neighbor = getNeighbor(face);
        if (neighbor == null) return;

        var holder = neighbor.getHolder();
        if (holder == null) return;

        holder.reload();
    }

    @Override
    public void reload() {
        dispatchChangeEvent(LogisticChangeType.CHANGED);
    }

    public void dispatchChangeEvent(LogisticChangeType logisticChangeType) {
        EventBusUtil.dispatchIfListening(
                createContainerChangedEvent(logisticChangeType, this)
        );
    }

    protected abstract LogisticComponentChangedEvent<TContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<TContainer> component);

    @Nullable
    public abstract Component<ChunkStore> clone();

    /// Comma-separated per-face configuration, for `toString` and the read interaction.
    ///
    /// Every component printed this the same way; keeping it here means the format stays
    /// consistent across resource types.
    protected String describeFaces() {
        return Arrays.stream(this.blockFaceConfig.getCurrentStates())
                .map(BlockFaceConfigState::toString)
                .collect(Collectors.joining(", "));
    }
}
