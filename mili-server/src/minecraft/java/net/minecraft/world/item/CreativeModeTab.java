package net.minecraft.world.item;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class CreativeModeTab {
    static final Identifier DEFAULT_BACKGROUND = createTextureLocation("items");
    private final Component displayName;
    Identifier backgroundTexture = DEFAULT_BACKGROUND;
    boolean canScroll = true;
    boolean showTitle = true;
    boolean alignedRight = false;
    private final CreativeModeTab.Row row;
    private final int column;
    private final CreativeModeTab.Type type;
    private @Nullable ItemStack iconItemStack;
    private Collection<ItemStack> displayItems = ItemStackLinkedSet.createTypeAndComponentsSet();
    private Set<ItemStack> displayItemsSearchTab = ItemStackLinkedSet.createTypeAndComponentsSet();
    private final Supplier<ItemStack> iconGenerator;
    private final CreativeModeTab.DisplayItemsGenerator displayItemsGenerator;

    CreativeModeTab(
        CreativeModeTab.Row row,
        int column,
        CreativeModeTab.Type type,
        Component displayName,
        Supplier<ItemStack> iconGenerator,
        CreativeModeTab.DisplayItemsGenerator displayItemsGenerator
    ) {
        this.row = row;
        this.column = column;
        this.displayName = displayName;
        this.iconGenerator = iconGenerator;
        this.displayItemsGenerator = displayItemsGenerator;
        this.type = type;
    }

    public static Identifier createTextureLocation(String name) {
        return Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_" + name + ".png");
    }

    public static CreativeModeTab.Builder builder(CreativeModeTab.Row row, int column) {
        return new CreativeModeTab.Builder(row, column);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public ItemStack getIconItem() {
        if (this.iconItemStack == null) {
            this.iconItemStack = this.iconGenerator.get();
        }

        return this.iconItemStack;
    }

    public Identifier getBackgroundTexture() {
        return this.backgroundTexture;
    }

    public boolean showTitle() {
        return this.showTitle;
    }

    public boolean canScroll() {
        return this.canScroll;
    }

    public int column() {
        return this.column;
    }

    public CreativeModeTab.Row row() {
        return this.row;
    }

    public boolean hasAnyItems() {
        return !this.displayItems.isEmpty();
    }

    public boolean shouldDisplay() {
        return this.type != CreativeModeTab.Type.CATEGORY || this.hasAnyItems();
    }

    public boolean isAlignedRight() {
        return this.alignedRight;
    }

    public CreativeModeTab.Type getType() {
        return this.type;
    }

    public void buildContents(CreativeModeTab.ItemDisplayParameters parameters) {
        CreativeModeTab.ItemDisplayBuilder itemDisplayBuilder = new CreativeModeTab.ItemDisplayBuilder(this, parameters.enabledFeatures);
        ResourceKey<CreativeModeTab> resourceKey = BuiltInRegistries.CREATIVE_MODE_TAB
            .getResourceKey(this)
            .orElseThrow(() -> new IllegalStateException("Unregistered creative tab: " + this));
        this.displayItemsGenerator.accept(parameters, itemDisplayBuilder);
        this.displayItems = itemDisplayBuilder.tabContents;
        this.displayItemsSearchTab = itemDisplayBuilder.searchTabContents;
    }

    public Collection<ItemStack> getDisplayItems() {
        return this.displayItems;
    }

    public Collection<ItemStack> getSearchTabDisplayItems() {
        return this.displayItemsSearchTab;
    }

    public boolean contains(ItemStack stack) {
        return this.displayItemsSearchTab.contains(stack);
    }

    public static class Builder {
        private static final CreativeModeTab.DisplayItemsGenerator EMPTY_GENERATOR = (parameters, output) -> {};
        private final CreativeModeTab.Row row;
        private final int column;
        private Component displayName = Component.empty();
        private Supplier<ItemStack> iconGenerator = () -> ItemStack.EMPTY;
        private CreativeModeTab.DisplayItemsGenerator displayItemsGenerator = EMPTY_GENERATOR;
        private boolean canScroll = true;
        private boolean showTitle = true;
        private boolean alignedRight = false;
        private CreativeModeTab.Type type = CreativeModeTab.Type.CATEGORY;
        private Identifier backgroundTexture = CreativeModeTab.DEFAULT_BACKGROUND;

        public Builder(CreativeModeTab.Row row, int column) {
            this.row = row;
            this.column = column;
        }

        public CreativeModeTab.Builder title(Component title) {
            this.displayName = title;
            return this;
        }

        public CreativeModeTab.Builder icon(Supplier<ItemStack> icon) {
            this.iconGenerator = icon;
            return this;
        }

        public CreativeModeTab.Builder displayItems(CreativeModeTab.DisplayItemsGenerator displayItemsGenerator) {
            this.displayItemsGenerator = displayItemsGenerator;
            return this;
        }

        public CreativeModeTab.Builder alignedRight() {
            this.alignedRight = true;
            return this;
        }

        public CreativeModeTab.Builder hideTitle() {
            this.showTitle = false;
            return this;
        }

        public CreativeModeTab.Builder noScrollBar() {
            this.canScroll = false;
            return this;
        }

        protected CreativeModeTab.Builder type(CreativeModeTab.Type type) {
            this.type = type;
            return this;
        }

        public CreativeModeTab.Builder backgroundTexture(Identifier backgroundTexture) {
            this.backgroundTexture = backgroundTexture;
            return this;
        }

        public CreativeModeTab build() {
            if ((this.type == CreativeModeTab.Type.HOTBAR || this.type == CreativeModeTab.Type.INVENTORY) && this.displayItemsGenerator != EMPTY_GENERATOR) {
                throw new IllegalStateException("Special tabs can't have display items");
            } else {
                CreativeModeTab creativeModeTab = new CreativeModeTab(
                    this.row, this.column, this.type, this.displayName, this.iconGenerator, this.displayItemsGenerator
                );
                creativeModeTab.alignedRight = this.alignedRight;
                creativeModeTab.showTitle = this.showTitle;
                creativeModeTab.canScroll = this.canScroll;
                creativeModeTab.backgroundTexture = this.backgroundTexture;
                return creativeModeTab;
            }
        }
    }

    @FunctionalInterface
    public interface DisplayItemsGenerator {
        void accept(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output);
    }

    static class ItemDisplayBuilder implements CreativeModeTab.Output {
        public final Collection<ItemStack> tabContents = ItemStackLinkedSet.createTypeAndComponentsSet();
        public final Set<ItemStack> searchTabContents = ItemStackLinkedSet.createTypeAndComponentsSet();
        private final CreativeModeTab tab;
        private final FeatureFlagSet featureFlagSet;

        public ItemDisplayBuilder(CreativeModeTab tab, FeatureFlagSet featureFlagSet) {
            this.tab = tab;
            this.featureFlagSet = featureFlagSet;
        }

        @Override
        public void accept(ItemStack stack, CreativeModeTab.TabVisibility tabVisibility) {
            if (stack.getCount() != 1) {
                throw new IllegalArgumentException("Stack size must be exactly 1");
            } else {
                boolean flag = this.tabContents.contains(stack) && tabVisibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY;
                if (flag) {
                    throw new IllegalStateException(
                        "Accidentally adding the same item stack twice "
                            + stack.getDisplayName().getString()
                            + " to a Creative Mode Tab: "
                            + this.tab.getDisplayName().getString()
                    );
                } else {
                    if (stack.getItem().isEnabled(this.featureFlagSet)) {
                        switch (tabVisibility) {
                            case PARENT_AND_SEARCH_TABS:
                                this.tabContents.add(stack);
                                this.searchTabContents.add(stack);
                                break;
                            case PARENT_TAB_ONLY:
                                this.tabContents.add(stack);
                                break;
                            case SEARCH_TAB_ONLY:
                                this.searchTabContents.add(stack);
                        }
                    }
                }
            }
        }
    }

    public record ItemDisplayParameters(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders) {
        public boolean needsUpdate(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders) {
            return !this.enabledFeatures.equals(enabledFeatures) || this.hasPermissions != hasPermissions || this.holders != holders;
        }
    }

    public interface Output {
        void accept(ItemStack stack, CreativeModeTab.TabVisibility tabVisibility);

        default void accept(ItemStack stack) {
            this.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void accept(ItemLike item, CreativeModeTab.TabVisibility tabVisibility) {
            this.accept(new ItemStack(item), tabVisibility);
        }

        default void accept(ItemLike item) {
            this.accept(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void acceptAll(Collection<ItemStack> stacks, CreativeModeTab.TabVisibility tabVisibility) {
            stacks.forEach(stack -> this.accept(stack, tabVisibility));
        }

        default void acceptAll(Collection<ItemStack> stacks) {
            this.acceptAll(stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    public static enum Row {
        TOP,
        BOTTOM;
    }

    protected static enum TabVisibility {
        PARENT_AND_SEARCH_TABS,
        PARENT_TAB_ONLY,
        SEARCH_TAB_ONLY;
    }

    public static enum Type {
        CATEGORY,
        INVENTORY,
        HOTBAR,
        SEARCH;
    }
}
