package net.minecraft.core.cauldron;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;

public interface CauldronInteraction {
    Map<String, CauldronInteraction.InteractionMap> INTERACTIONS = new Object2ObjectArrayMap<>();
    Codec<CauldronInteraction.InteractionMap> CODEC = Codec.stringResolver(CauldronInteraction.InteractionMap::name, INTERACTIONS::get);
    CauldronInteraction.InteractionMap EMPTY = newInteractionMap("empty");
    CauldronInteraction.InteractionMap WATER = newInteractionMap("water");
    CauldronInteraction.InteractionMap LAVA = newInteractionMap("lava");
    CauldronInteraction.InteractionMap POWDER_SNOW = newInteractionMap("powder_snow");

    static CauldronInteraction.InteractionMap newInteractionMap(String name) {
        Object2ObjectOpenHashMap<Item, CauldronInteraction> map = new Object2ObjectOpenHashMap<>();
        map.defaultReturnValue((state, level, pos, player, hand, stack, hitDirection) -> InteractionResult.TRY_WITH_EMPTY_HAND); // Paper - add hitDirection
        CauldronInteraction.InteractionMap interactionMap = new CauldronInteraction.InteractionMap(name, map);
        INTERACTIONS.put(name, interactionMap);
        return interactionMap;
    }

    InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, final net.minecraft.core.Direction hitDirection); // Paper - add hitDirection

    static void bootStrap() {
        Map<Item, CauldronInteraction> map = EMPTY.map();
        addDefaultInteractions(map);
        map.put(Items.POTION, (state, level, pos, player, hand, stack, hitDirection) -> { // Paper - add hitDirection
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null && potionContents.is(Potions.WATER)) {
                if (!level.isClientSide()) {
                    // CraftBukkit start
                    if (!LayeredCauldronBlock.changeLevel(level, pos, Blocks.WATER_CAULDRON.defaultBlockState(), player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY, false)) { // Paper - Call CauldronLevelChangeEvent
                        return InteractionResult.SUCCESS;
                    }
                    // CraftBukkit end
                    Item item = stack.getItem();
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    player.awardStat(Stats.USE_CAULDRON);
                    player.awardStat(Stats.ITEM_USED.get(item));
                    // level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState()); // CraftBukkit
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
        });
        Map<Item, CauldronInteraction> map1 = WATER.map();
        addDefaultInteractions(map1);
        map1.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack, hitDirection) -> fillBucket( // Paper - add hitDirection
                state,
                level,
                pos,
                player,
                hand,
                stack,
                new ItemStack(Items.WATER_BUCKET),
                blockState -> blockState.getValue(LayeredCauldronBlock.LEVEL) == 3,
                SoundEvents.BUCKET_FILL, hitDirection // Paper - add hitDirection
            )
        );
        map1.put(Items.GLASS_BOTTLE, (state, level, pos, player, hand, stack, hitDirection) -> { // Paper - add hitDirection
            if (!level.isClientSide()) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(state, level, pos, player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.BOTTLE_FILL)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                Item item = stack.getItem();
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(item));
                // LayeredCauldronBlock.lowerFillLevel(state, level, pos); // CraftBukkit
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }

            return InteractionResult.SUCCESS;
        });
        map1.put(Items.POTION, (state, level, pos, player, hand, stack, hitDirection) -> { // Paper - add hitDirection
            if (state.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            } else {
                PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
                if (potionContents != null && potionContents.is(Potions.WATER)) {
                    if (!level.isClientSide()) {
                        // CraftBukkit start
                        if (!LayeredCauldronBlock.changeLevel(level, pos, state.cycle(LayeredCauldronBlock.LEVEL), player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY, false)) { // Paper - Call CauldronLevelChangeEvent
                            return InteractionResult.SUCCESS;
                        }
                        // CraftBukkit end
                        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                        player.awardStat(Stats.USE_CAULDRON);
                        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                        // level.setBlockAndUpdate(pos, state.cycle(LayeredCauldronBlock.LEVEL)); // CraftBukkit
                        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                    }

                    return InteractionResult.SUCCESS;
                } else {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
            }
        });
        map1.put(Items.LEATHER_BOOTS, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_LEGGINGS, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_CHESTPLATE, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_HELMET, CauldronInteraction::dyedItemIteration);
        map1.put(Items.LEATHER_HORSE_ARMOR, CauldronInteraction::dyedItemIteration);
        map1.put(Items.WOLF_ARMOR, CauldronInteraction::dyedItemIteration);
        map1.put(Items.WHITE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.GRAY_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.BLACK_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.BLUE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.BROWN_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.CYAN_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.GREEN_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.LIGHT_BLUE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.LIGHT_GRAY_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.LIME_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.MAGENTA_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.ORANGE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.PINK_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.PURPLE_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.RED_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.YELLOW_BANNER, CauldronInteraction::bannerInteraction);
        map1.put(Items.WHITE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.GRAY_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.BLACK_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.BLUE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.BROWN_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.CYAN_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.GREEN_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.LIGHT_BLUE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.LIGHT_GRAY_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.LIME_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.MAGENTA_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.ORANGE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.PINK_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.PURPLE_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.RED_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        map1.put(Items.YELLOW_SHULKER_BOX, CauldronInteraction::shulkerBoxInteraction);
        Map<Item, CauldronInteraction> map2 = LAVA.map();
        map2.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack, hitDirection) -> fillBucket( // Paper - add hitDirection
                state, level, pos, player, hand, stack, new ItemStack(Items.LAVA_BUCKET), blockState -> true, SoundEvents.BUCKET_FILL_LAVA, hitDirection // Paper - add hitDirection
            )
        );
        addDefaultInteractions(map2);
        Map<Item, CauldronInteraction> map3 = POWDER_SNOW.map();
        map3.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack, hitDirection) -> fillBucket( // Paper - add hitDirection
                state,
                level,
                pos,
                player,
                hand,
                stack,
                new ItemStack(Items.POWDER_SNOW_BUCKET),
                blockState -> blockState.getValue(LayeredCauldronBlock.LEVEL) == 3,
                SoundEvents.BUCKET_FILL_POWDER_SNOW, hitDirection // Paper - add hitDirection
            )
        );
        addDefaultInteractions(map3);
    }

    static void addDefaultInteractions(Map<Item, CauldronInteraction> interactionsMap) {
        interactionsMap.put(Items.LAVA_BUCKET, CauldronInteraction::fillLavaInteraction);
        interactionsMap.put(Items.WATER_BUCKET, CauldronInteraction::fillWaterInteraction);
        interactionsMap.put(Items.POWDER_SNOW_BUCKET, CauldronInteraction::fillPowderSnowInteraction);
    }

    static InteractionResult fillBucket(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        ItemStack emptyStack,
        ItemStack filledStack,
        Predicate<BlockState> statePredicate,
        SoundEvent fillSound
    ) {
        // Paper start - add hitDirection
        return fillBucket(state, level, pos, player, hand, emptyStack, filledStack, statePredicate, fillSound, null); // Paper - add hitDirection
    }

    static InteractionResult fillBucket(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack emptyStack, ItemStack filledStack, Predicate<BlockState> statePredicate, SoundEvent fillSound, @javax.annotation.Nullable net.minecraft.core.Direction hitDirection) {
        // Paper end - add hitDirection
        if (!statePredicate.test(state)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                // Paper start - fire PlayerBucketFillEvent
                if (hitDirection != null) {
                    org.bukkit.event.player.PlayerBucketEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketFillEvent(level, player, pos, pos, hitDirection, emptyStack, filledStack.getItem(), hand);
                    if (event.isCancelled()) {
                        return InteractionResult.PASS;
                    }
                    filledStack = event.getItemStack() != null ? org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack()) : ItemStack.EMPTY;
                }
                // Paper end - fire PlayerBucketFillEvent
                // CraftBukkit start
                if (!LayeredCauldronBlock.changeLevel(level, pos, Blocks.CAULDRON.defaultBlockState(), player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL, false)) { // Paper - Call CauldronLevelChangeEvent
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                Item item = emptyStack.getItem();
                player.setItemInHand(hand, ItemUtils.createFilledResult(emptyStack, player, filledStack));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(item));
                // level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState()); // CraftBukkit
                level.playSound(null, pos, fillSound, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    static InteractionResult emptyBucket(
        Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack filledStack, BlockState state, SoundEvent emptySound
    ) {
        // Paper start - add hitDirection
        return emptyBucket(level, pos, player, hand, filledStack, state, emptySound, null);
    }

    static InteractionResult emptyBucket(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack filledStack, BlockState state, SoundEvent emptySound, @javax.annotation.Nullable net.minecraft.core.Direction hitDirection) {
        // Paper end - add hitDirection
        if (!level.isClientSide()) {
            // Paper start - fire PlayerBucketEmptyEvent
            ItemStack output = new ItemStack(Items.BUCKET);
            if (hitDirection != null) {
                org.bukkit.event.player.PlayerBucketEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketEmptyEvent(level, player, pos, pos, hitDirection, filledStack, hand);
                if (event.isCancelled()) {
                    return InteractionResult.PASS;
                }
                output = event.getItemStack() != null ? org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack()) : ItemStack.EMPTY;
            }
            // Paper end - fire PlayerBucketEmptyEvent
            // CraftBukkit start
            if (!LayeredCauldronBlock.changeLevel(level, pos, state, player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY, false)) { // Paper - Call CauldronLevelChangeEvent
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end
            Item item = filledStack.getItem();
            player.setItemInHand(hand, ItemUtils.createFilledResult(filledStack, player, output)); // Paper
            player.awardStat(Stats.FILL_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            // level.setBlockAndUpdate(pos, state); // CraftBukkit
            level.playSound(null, pos, emptySound, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
        }

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult fillWaterInteraction(
        BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack filledStack, final net.minecraft.core.Direction hitDirection // Paper - add hitDirection
    ) {
        return emptyBucket(
            level, pos, player, hand, filledStack, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY, hitDirection // Paper - add hitDirection
        );
    }

    private static InteractionResult fillLavaInteraction(
        BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack filledStack, final net.minecraft.core.Direction hitDirection // Paper - add hitDirection
    ) {
        return (InteractionResult)(isUnderWater(level, pos)
            ? InteractionResult.CONSUME
            : emptyBucket(level, pos, player, hand, filledStack, Blocks.LAVA_CAULDRON.defaultBlockState(), SoundEvents.BUCKET_EMPTY_LAVA, hitDirection)); // Paper - add hitDirection
    }

    private static InteractionResult fillPowderSnowInteraction(
        BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack filledStack, final net.minecraft.core.Direction hitDirection // Paper - add hitDirection
    ) {
        return (InteractionResult)(isUnderWater(level, pos)
            ? InteractionResult.CONSUME
            : emptyBucket(
                level,
                pos,
                player,
                hand,
                filledStack,
                Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3),
                SoundEvents.BUCKET_EMPTY_POWDER_SNOW, hitDirection // Paper - add hitDirection
            ));
    }

    private static InteractionResult shulkerBoxInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, final net.minecraft.core.Direction hitDirection) { // Paper - add hitDirection
        Block block = Block.byItem(stack.getItem());
        if (!(block instanceof ShulkerBoxBlock)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(state, level, pos, player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.SHULKER_WASH)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                ItemStack itemStack = stack.transmuteCopy(Blocks.SHULKER_BOX, 1);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, itemStack, false));
                player.awardStat(Stats.CLEAN_SHULKER_BOX);
                // LayeredCauldronBlock.lowerFillLevel(state, level, pos); // CraftBukkit
            }

            return InteractionResult.SUCCESS;
        }
    }

    private static InteractionResult bannerInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, final net.minecraft.core.Direction hitDirection) { // Paper - add hitDirection
        BannerPatternLayers bannerPatternLayers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        if (bannerPatternLayers.layers().isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(state, level, pos, player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.BANNER_WASH)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                ItemStack itemStack = stack.copyWithCount(1);
                itemStack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers.removeLast());
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, itemStack, false));
                player.awardStat(Stats.CLEAN_BANNER);
                // LayeredCauldronBlock.lowerFillLevel(state, level, pos); // CraftBukkit
            }

            return InteractionResult.SUCCESS;
        }
    }

    private static InteractionResult dyedItemIteration(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, final net.minecraft.core.Direction hitDirection) { // Paper - add hitDirection
        if (!stack.is(ItemTags.DYEABLE)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else if (!stack.has(DataComponents.DYED_COLOR)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            if (!level.isClientSide()) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(state, level, pos, player, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.ARMOR_WASH)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                stack.remove(DataComponents.DYED_COLOR);
                player.awardStat(Stats.CLEAN_ARMOR);
                // LayeredCauldronBlock.lowerFillLevel(state, level, pos); // CraftBukkit
            }

            return InteractionResult.SUCCESS;
        }
    }

    private static boolean isUnderWater(Level level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos.above());
        return fluidState.is(FluidTags.WATER);
    }

    public record InteractionMap(String name, Map<Item, CauldronInteraction> map) {
    }
}
