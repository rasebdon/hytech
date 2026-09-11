package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// The payload every Hytech page click sends back: which button was pressed.
///
/// The key is `Action`, without an `@` — a leading `@` marks a value as a selector the client
/// resolves at event time, which fails for a literal action name.
public final class PageAction {

    @Nonnull
    public static final BuilderCodec<PageAction> CODEC =
            BuilderCodec.builder(PageAction.class, PageAction::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING),
                            (data, value) -> data.action = value,
                            (data) -> data.action)
                    .add()
                    .build();

    @Nullable
    private String action;

    public PageAction() {
    }

    @Nullable
    public String action() {
        return this.action;
    }
}
