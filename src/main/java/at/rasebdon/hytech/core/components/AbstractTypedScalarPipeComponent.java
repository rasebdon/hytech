package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;
import java.util.Map;

/// A pipe segment carrying one typed resource.
///
/// Records which resource it held at save time alongside how much, so a run of fluid pipes
/// comes back carrying water rather than an untyped quantity of nothing.
public abstract class AbstractTypedScalarPipeComponent<TContainer>
        extends AbstractScalarPipeComponent<TContainer> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<AbstractTypedScalarPipeComponent> CODEC =
            BuilderCodec.abstractBuilder(AbstractTypedScalarPipeComponent.class,
                            AbstractScalarPipeComponent.CODEC)
                    .append(new KeyedCodec<>("SavedAmount", Codec.LONG),
                            (c, v) -> c.savedAmount = v,
                            (c) -> c.savedAmount)
                    .documentation("Amount held by this segment when its chunk was last saved").add()
                    .append(new KeyedCodec<>("SavedResourceType", Codec.STRING),
                            (c, v) -> c.savedResourceType = v,
                            (c) -> c.savedResourceType)
                    .documentation("Resource held by this segment when its chunk was last saved").add()
                    .build();

    @Nullable
    protected String savedResourceType;

    protected AbstractTypedScalarPipeComponent(
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames,
            long savedAmount,
            long pipeCapacity,
            long pipeTransferSpeed,
            @Nullable String savedResourceType) {
        super(blockFaceConfig, connectionModelAssetNames, savedAmount, pipeCapacity, pipeTransferSpeed);

        this.savedResourceType = savedResourceType;

        // Same invariant as the tank: a quantity with no type is unreachable.
        if (this.savedResourceType == null || this.savedResourceType.isBlank()) {
            this.savedResourceType = null;
            this.savedAmount = 0L;
        }
    }

    @Nullable
    public String getSavedResourceType() {
        return this.savedResourceType;
    }

    public void setSavedResourceType(@Nullable String resourceType) {
        this.savedResourceType = resourceType == null || resourceType.isBlank() ? null : resourceType;

        if (this.savedResourceType == null) {
            this.savedAmount = 0L;
        }
    }
}
