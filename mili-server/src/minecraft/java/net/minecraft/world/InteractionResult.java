package net.minecraft.world;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public sealed interface InteractionResult
    permits InteractionResult.Success,
    InteractionResult.Fail,
    InteractionResult.Pass,
    InteractionResult.TryEmptyHandInteraction {
    InteractionResult.Success SUCCESS = new InteractionResult.Success(InteractionResult.SwingSource.CLIENT, InteractionResult.ItemContext.DEFAULT);
    InteractionResult.Success SUCCESS_SERVER = new InteractionResult.Success(InteractionResult.SwingSource.SERVER, InteractionResult.ItemContext.DEFAULT);
    InteractionResult.Success CONSUME = new InteractionResult.Success(InteractionResult.SwingSource.NONE, InteractionResult.ItemContext.DEFAULT);
    InteractionResult.Fail FAIL = new InteractionResult.Fail();
    InteractionResult.Pass PASS = new InteractionResult.Pass();
    InteractionResult.TryEmptyHandInteraction TRY_WITH_EMPTY_HAND = new InteractionResult.TryEmptyHandInteraction();

    default boolean consumesAction() {
        return false;
    }

    public record Fail() implements InteractionResult {
    }

    public record ItemContext(boolean wasItemInteraction, @Nullable ItemStack heldItemTransformedTo) {
        static InteractionResult.ItemContext NONE = new InteractionResult.ItemContext(false, null);
        static InteractionResult.ItemContext DEFAULT = new InteractionResult.ItemContext(true, null);
    }

    public record Pass() implements InteractionResult {
    }

    // Paper start - track more context in interaction result
    public record PaperSuccessContext(net.minecraft.core.@Nullable BlockPos placedBlockPosition) {
        static PaperSuccessContext DEFAULT = new PaperSuccessContext(null);

        public PaperSuccessContext placedBlockAt(final net.minecraft.core.BlockPos blockPos) {
            return new PaperSuccessContext(blockPos);
        }
    }
    public record Success(InteractionResult.SwingSource swingSource, InteractionResult.ItemContext itemContext, PaperSuccessContext paperSuccessContext) implements InteractionResult {
        public InteractionResult.Success configurePaper(final java.util.function.UnaryOperator<PaperSuccessContext> edit) {
            return new InteractionResult.Success(this.swingSource, this.itemContext, edit.apply(this.paperSuccessContext));
        }

        public Success(final net.minecraft.world.InteractionResult.SwingSource swingSource, final net.minecraft.world.InteractionResult.ItemContext itemContext) {
            this(swingSource, itemContext, PaperSuccessContext.DEFAULT);
        }
    // Paper end - track more context in interaction result
        @Override
        public boolean consumesAction() {
            return true;
        }

        public InteractionResult.Success heldItemTransformedTo(ItemStack stack) {
            return new InteractionResult.Success(this.swingSource, new InteractionResult.ItemContext(true, stack), this.paperSuccessContext); // Paper - track more context in interaction result
        }

        public InteractionResult.Success withoutItem() {
            return new InteractionResult.Success(this.swingSource, InteractionResult.ItemContext.NONE, this.paperSuccessContext); // Paper - track more context in interaction result
        }

        public boolean wasItemInteraction() {
            return this.itemContext.wasItemInteraction;
        }

        public @Nullable ItemStack heldItemTransformedTo() {
            return this.itemContext.heldItemTransformedTo;
        }
    }

    public static enum SwingSource {
        NONE,
        CLIENT,
        SERVER;
    }

    public record TryEmptyHandInteraction() implements InteractionResult {
    }
}
