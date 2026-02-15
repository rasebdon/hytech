package at.rasebdon.hytech.core.transport;

import org.jetbrains.annotations.NotNull;

public record BlockFaceConfigState(BlockFaceConfigType type, int index) {
    @Override
    public @NotNull String toString() {
        return type.name() + "_" + index;
    }
}
