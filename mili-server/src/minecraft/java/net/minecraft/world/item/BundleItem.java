package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.math.Fraction;

public class BundleItem extends Item {
    public static final int MAX_SHOWN_GRID_ITEMS_X = 4;
    public static final int MAX_SHOWN_GRID_ITEMS_Y = 3;
    public static final int MAX_SHOWN_GRID_ITEMS = 12;
    public static final int OVERFLOWING_MAX_SHOWN_GRID_ITEMS = 11;
    private static final int FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F);
    private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.44F, 0.53F, 1.0F);
    private static final int TICKS_AFTER_FIRST_THROW = 10;
    private static final int TICKS_BETWEEN_THROWS = 2;
    private static final int TICKS_MAX_THROW_DURATION = 200;

    public BundleItem(Item.Properties properties) {
        super(properties);
    }

    public static float getFullnessDisplay(ItemStack stack) {
        BundleContents bundleContents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundleContents.weight().floatValue();
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents == null) {
            return false;
        } else {
            ItemStack item = slot.getItem();
            BundleContents.Mutable mutable = new BundleContents.Mutable(bundleContents);
            if (action == ClickAction.PRIMARY && !item.isEmpty()) {
                if (mutable.tryTransfer(slot, player) > 0) {
                    playInsertSound(player);
                } else {
                    playInsertFailSound(player);
                }

                stack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
                this.broadcastChangesOnContainerMenu(player);
                return true;
            } else if (action == ClickAction.SECONDARY && item.isEmpty()) {
                ItemStack itemStack = mutable.removeOne();
                if (itemStack != null) {
                    ItemStack itemStack1 = slot.safeInsert(itemStack);
                    if (itemStack1.getCount() > 0) {
                        mutable.tryInsert(itemStack1);
                    } else {
                        playRemoveOneSound(player);
                    }
                }

                stack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
                this.broadcastChangesOnContainerMenu(player);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action == ClickAction.PRIMARY && other.isEmpty()) {
            toggleSelectedItem(stack, -1);
            return false;
        } else {
            BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundleContents == null) {
                return false;
            } else {
                BundleContents.Mutable mutable = new BundleContents.Mutable(bundleContents);
                if (action == ClickAction.PRIMARY && !other.isEmpty()) {
                    if (slot.allowModification(player) && mutable.tryInsert(other) > 0) {
                        playInsertSound(player);
                    } else {
                        playInsertFailSound(player);
                    }

                    stack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
                    this.broadcastChangesOnContainerMenu(player);
                    return true;
                } else if (action == ClickAction.SECONDARY && other.isEmpty()) {
                    if (slot.allowModification(player)) {
                        ItemStack itemStack = mutable.removeOne();
                        if (itemStack != null) {
                            playRemoveOneSound(player);
                            access.set(itemStack);
                        }
                    }

                    stack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
                    this.broadcastChangesOnContainerMenu(player);
                    return true;
                } else {
                    toggleSelectedItem(stack, -1);
                    return false;
                }
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    private void dropContent(Level level, Player player, ItemStack stack) {
        if (this.dropContent(stack, player)) {
            playDropContentsSound(level, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        BundleContents bundleContents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundleContents.weight().compareTo(Fraction.ZERO) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        BundleContents bundleContents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return Math.min(1 + Mth.mulAndTruncate(bundleContents.weight(), 12), 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BundleContents bundleContents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundleContents.weight().compareTo(Fraction.ONE) >= 0 ? FULL_BAR_COLOR : BAR_COLOR;
    }

    public static void toggleSelectedItem(ItemStack bundle, int selectedItem) {
        BundleContents bundleContents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            BundleContents.Mutable mutable = new BundleContents.Mutable(bundleContents);
            mutable.toggleSelectedItem(selectedItem);
            bundle.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
        }
    }

    public static boolean hasSelectedItem(ItemStack bundle) {
        BundleContents bundleContents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        return bundleContents != null && bundleContents.getSelectedItem() != -1;
    }

    public static int getSelectedItem(ItemStack bundle) {
        BundleContents bundleContents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundleContents.getSelectedItem();
    }

    public static ItemStack getSelectedItemStack(ItemStack bundle) {
        BundleContents bundleContents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        return bundleContents != null && bundleContents.getSelectedItem() != -1
            ? bundleContents.getItemUnsafe(bundleContents.getSelectedItem())
            : ItemStack.EMPTY;
    }

    public static int getNumberOfItemsToShow(ItemStack bundle) {
        BundleContents bundleContents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundleContents.getNumberOfItemsToShow();
    }

    private boolean dropContent(ItemStack stack, Player player) {
        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null && !bundleContents.isEmpty()) {
            Optional<ItemStack> optional = removeOneItemFromBundle(stack, player, bundleContents);
            if (optional.isPresent()) {
                player.drop(optional.get(), true);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static Optional<ItemStack> removeOneItemFromBundle(ItemStack stack, Player player, BundleContents bundleContents) {
        BundleContents.Mutable mutable = new BundleContents.Mutable(bundleContents);
        ItemStack itemStack = mutable.removeOne();
        if (itemStack != null) {
            playRemoveOneSound(player);
            stack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
            return Optional.of(itemStack);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (livingEntity instanceof Player player) {
            int useDuration = this.getUseDuration(stack, livingEntity);
            boolean flag = remainingUseDuration == useDuration;
            if (flag || remainingUseDuration < useDuration - 10 && remainingUseDuration % 2 == 0) {
                this.dropContent(level, player, stack);
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 200;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BUNDLE;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        TooltipDisplay tooltipDisplay = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        return !tooltipDisplay.shows(DataComponents.BUNDLE_CONTENTS)
            ? Optional.empty()
            : Optional.ofNullable(stack.get(DataComponents.BUNDLE_CONTENTS)).map(BundleTooltip::new);
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        BundleContents bundleContents = itemEntity.getItem().get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            itemEntity.getItem().set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            ItemUtils.onContainerDestroyed(itemEntity, bundleContents.itemsCopy());
        }
    }

    public static List<BundleItem> getAllBundleItemColors() {
        return Stream.of(
                Items.BUNDLE,
                Items.WHITE_BUNDLE,
                Items.ORANGE_BUNDLE,
                Items.MAGENTA_BUNDLE,
                Items.LIGHT_BLUE_BUNDLE,
                Items.YELLOW_BUNDLE,
                Items.LIME_BUNDLE,
                Items.PINK_BUNDLE,
                Items.GRAY_BUNDLE,
                Items.LIGHT_GRAY_BUNDLE,
                Items.CYAN_BUNDLE,
                Items.BLACK_BUNDLE,
                Items.BROWN_BUNDLE,
                Items.GREEN_BUNDLE,
                Items.RED_BUNDLE,
                Items.BLUE_BUNDLE,
                Items.PURPLE_BUNDLE
            )
            .map(item -> (BundleItem)item)
            .toList();
    }

    public static Item getByColor(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_BUNDLE;
            case ORANGE -> Items.ORANGE_BUNDLE;
            case MAGENTA -> Items.MAGENTA_BUNDLE;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_BUNDLE;
            case YELLOW -> Items.YELLOW_BUNDLE;
            case LIME -> Items.LIME_BUNDLE;
            case PINK -> Items.PINK_BUNDLE;
            case GRAY -> Items.GRAY_BUNDLE;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_BUNDLE;
            case CYAN -> Items.CYAN_BUNDLE;
            case BLUE -> Items.BLUE_BUNDLE;
            case BROWN -> Items.BROWN_BUNDLE;
            case GREEN -> Items.GREEN_BUNDLE;
            case RED -> Items.RED_BUNDLE;
            case BLACK -> Items.BLACK_BUNDLE;
            case PURPLE -> Items.PURPLE_BUNDLE;
        };
    }

    private static void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertFailSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
    }

    private static void playDropContentsSound(Level level, Entity entity) {
        level.playSound(
            null, entity.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F
        );
    }

    private void broadcastChangesOnContainerMenu(Player player) {
        AbstractContainerMenu abstractContainerMenu = player.containerMenu;
        if (abstractContainerMenu != null) {
            abstractContainerMenu.slotsChanged(player.getInventory());
        }
    }
}
