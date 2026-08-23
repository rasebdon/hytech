package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// The payload every Hytech page click sends back: which button was pressed.
///
/// Hytale delivers one decoded object per page, not per element, so a page with several buttons
/// needs a discriminator, and each binding carries its own action name.
///
/// The key is `Action`, deliberately *without* an `@`. A leading `@` marks a binding value as
/// **dynamic** -- the client reads it as a selector at event time -- so `@Action` with a literal
/// made the client try to resolve "configure" as a selector and fail with
/// "Failed to gather CustomUI event binding". Static literals take an unprefixed key.
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
