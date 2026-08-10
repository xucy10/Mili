package net.minecraft.world.item;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;

public class HoneycombItem extends Item implements SignApplicator {
    public static final Supplier<BiMap<Block, Block>> WAXABLES = Suppliers.memoize(
        () -> ImmutableBiMap.<Block, Block>builder()
            .put(Blocks.COPPER_BLOCK, Blocks.WAXED_COPPER_BLOCK)
            .put(Blocks.EXPOSED_COPPER, Blocks.WAXED_EXPOSED_COPPER)
            .put(Blocks.WEATHERED_COPPER, Blocks.WAXED_WEATHERED_COPPER)
            .put(Blocks.OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_COPPER)
            .put(Blocks.CUT_COPPER, Blocks.WAXED_CUT_COPPER)
            .put(Blocks.EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER)
            .put(Blocks.WEATHERED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER)
            .put(Blocks.OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER)
            .put(Blocks.CUT_COPPER_SLAB, Blocks.WAXED_CUT_COPPER_SLAB)
            .put(Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB)
            .put(Blocks.WEATHERED_CUT_COPPER_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB)
            .put(Blocks.OXIDIZED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB)
            .put(Blocks.CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_STAIRS)
            .put(Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS)
            .put(Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS)
            .put(Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS)
            .put(Blocks.CHISELED_COPPER, Blocks.WAXED_CHISELED_COPPER)
            .put(Blocks.EXPOSED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER)
            .put(Blocks.WEATHERED_CHISELED_COPPER, Blocks.WAXED_WEATHERED_CHISELED_COPPER)
            .put(Blocks.OXIDIZED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER)
            .put(Blocks.COPPER_DOOR, Blocks.WAXED_COPPER_DOOR)
            .put(Blocks.EXPOSED_COPPER_DOOR, Blocks.WAXED_EXPOSED_COPPER_DOOR)
            .put(Blocks.WEATHERED_COPPER_DOOR, Blocks.WAXED_WEATHERED_COPPER_DOOR)
            .put(Blocks.OXIDIZED_COPPER_DOOR, Blocks.WAXED_OXIDIZED_COPPER_DOOR)
            .put(Blocks.COPPER_TRAPDOOR, Blocks.WAXED_COPPER_TRAPDOOR)
            .put(Blocks.EXPOSED_COPPER_TRAPDOOR, Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR)
            .put(Blocks.WEATHERED_COPPER_TRAPDOOR, Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR)
            .put(Blocks.OXIDIZED_COPPER_TRAPDOOR, Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR)
            .putAll(Blocks.COPPER_BARS.waxedMapping())
            .put(Blocks.COPPER_GRATE, Blocks.WAXED_COPPER_GRATE)
            .put(Blocks.EXPOSED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE)
            .put(Blocks.WEATHERED_COPPER_GRATE, Blocks.WAXED_WEATHERED_COPPER_GRATE)
            .put(Blocks.OXIDIZED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE)
            .put(Blocks.COPPER_BULB, Blocks.WAXED_COPPER_BULB)
            .put(Blocks.EXPOSED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB)
            .put(Blocks.WEATHERED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB)
            .put(Blocks.OXIDIZED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB)
            .put(Blocks.COPPER_CHEST, Blocks.WAXED_COPPER_CHEST)
            .put(Blocks.EXPOSED_COPPER_CHEST, Blocks.WAXED_EXPOSED_COPPER_CHEST)
            .put(Blocks.WEATHERED_COPPER_CHEST, Blocks.WAXED_WEATHERED_COPPER_CHEST)
            .put(Blocks.OXIDIZED_COPPER_CHEST, Blocks.WAXED_OXIDIZED_COPPER_CHEST)
            .put(Blocks.COPPER_GOLEM_STATUE, Blocks.WAXED_COPPER_GOLEM_STATUE)
            .put(Blocks.EXPOSED_COPPER_GOLEM_STATUE, Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE)
            .put(Blocks.WEATHERED_COPPER_GOLEM_STATUE, Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE)
            .put(Blocks.OXIDIZED_COPPER_GOLEM_STATUE, Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE)
            .put(Blocks.LIGHTNING_ROD, Blocks.WAXED_LIGHTNING_ROD)
            .put(Blocks.EXPOSED_LIGHTNING_ROD, Blocks.WAXED_EXPOSED_LIGHTNING_ROD)
            .put(Blocks.WEATHERED_LIGHTNING_ROD, Blocks.WAXED_WEATHERED_LIGHTNING_ROD)
            .put(Blocks.OXIDIZED_LIGHTNING_ROD, Blocks.WAXED_OXIDIZED_LIGHTNING_ROD)
            .putAll(Blocks.COPPER_LANTERN.waxedMapping())
            .putAll(Blocks.COPPER_CHAIN.waxedMapping())
            .build()
    );
    public static final Supplier<BiMap<Block, Block>> WAX_OFF_BY_BLOCK = Suppliers.memoize(() -> WAXABLES.get().inverse());
    private static final String WAXED_COPPER_DOOR = "waxed_copper_door";
    private static final String WAXED_COPPER_TRAPDOOR = "waxed_copper_trapdoor";
    private static final String WAXED_COPPER_GOLEM_STATUE = "waxed_copper_golem_statue";
    private static final String WAXED_COPPER_CHEST = "waxed_copper_chest";
    private static final String WAXED_LIGHTNING_ROD = "waxed_lightning_rod";
    private static final String WAXED_COPPER_BAR = "waxed_copper_bar";
    private static final String WAXED_COPPER_CHAIN = "waxed_copper_chain";
    private static final String WAXED_COPPER_LANTERN = "waxed_copper_lantern";
    private static final String WAXED_COPPER_BLOCK = "waxed_copper_block";
    public static final ImmutableMap<Block, Pair<RecipeCategory, String>> WAXED_RECIPES = ImmutableMap.<Block, Pair<RecipeCategory, String>>builder()
        .put(Blocks.WAXED_COPPER_BULB, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_bulb"))
        .put(Blocks.WAXED_WEATHERED_COPPER_BULB, Pair.of(RecipeCategory.REDSTONE, "waxed_weathered_copper_bulb"))
        .put(Blocks.WAXED_EXPOSED_COPPER_BULB, Pair.of(RecipeCategory.REDSTONE, "waxed_exposed_copper_bulb"))
        .put(Blocks.WAXED_OXIDIZED_COPPER_BULB, Pair.of(RecipeCategory.REDSTONE, "waxed_oxidized_copper_bulb"))
        .put(Blocks.WAXED_COPPER_DOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_door"))
        .put(Blocks.WAXED_WEATHERED_COPPER_DOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_door"))
        .put(Blocks.WAXED_EXPOSED_COPPER_DOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_door"))
        .put(Blocks.WAXED_OXIDIZED_COPPER_DOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_door"))
        .put(Blocks.WAXED_COPPER_TRAPDOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_trapdoor"))
        .put(Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_trapdoor"))
        .put(Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_trapdoor"))
        .put(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, Pair.of(RecipeCategory.REDSTONE, "waxed_copper_trapdoor"))
        .put(Blocks.WAXED_COPPER_GOLEM_STATUE, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_golem_statue"))
        .put(Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_golem_statue"))
        .put(Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_golem_statue"))
        .put(Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_golem_statue"))
        .put(Blocks.WAXED_COPPER_CHEST, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chest"))
        .put(Blocks.WAXED_WEATHERED_COPPER_CHEST, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chest"))
        .put(Blocks.WAXED_EXPOSED_COPPER_CHEST, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chest"))
        .put(Blocks.WAXED_OXIDIZED_COPPER_CHEST, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chest"))
        .put(Blocks.WAXED_LIGHTNING_ROD, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_lightning_rod"))
        .put(Blocks.WAXED_WEATHERED_LIGHTNING_ROD, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_lightning_rod"))
        .put(Blocks.WAXED_EXPOSED_LIGHTNING_ROD, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_lightning_rod"))
        .put(Blocks.WAXED_OXIDIZED_LIGHTNING_ROD, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_lightning_rod"))
        .put(Blocks.COPPER_BARS.waxed(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_bar"))
        .put(Blocks.COPPER_BARS.waxedWeathered(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_bar"))
        .put(Blocks.COPPER_BARS.waxedExposed(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_bar"))
        .put(Blocks.COPPER_BARS.waxedOxidized(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_bar"))
        .put(Blocks.COPPER_CHAIN.waxed(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chain"))
        .put(Blocks.COPPER_CHAIN.waxedWeathered(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chain"))
        .put(Blocks.COPPER_CHAIN.waxedExposed(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chain"))
        .put(Blocks.COPPER_CHAIN.waxedOxidized(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_chain"))
        .put(Blocks.COPPER_LANTERN.waxed(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_lantern"))
        .put(Blocks.COPPER_LANTERN.waxedWeathered(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_lantern"))
        .put(Blocks.COPPER_LANTERN.waxedExposed(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_lantern"))
        .put(Blocks.COPPER_LANTERN.waxedOxidized(), Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_lantern"))
        .put(Blocks.WAXED_COPPER_BLOCK, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_block"))
        .put(Blocks.WAXED_WEATHERED_COPPER, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_block"))
        .put(Blocks.WAXED_EXPOSED_COPPER, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_block"))
        .put(Blocks.WAXED_OXIDIZED_COPPER, Pair.of(RecipeCategory.BUILDING_BLOCKS, "waxed_copper_block"))
        .build();

    public HoneycombItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        return getWaxed(blockState).<InteractionResult>map(blockState1 -> {
            Player player = context.getPlayer();
            ItemStack itemInHand = context.getItemInHand();
            // Paper start - EntityChangeBlockEvent
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, clickedPos, blockState1)) {
                if (!player.isCreative()) {
                    player.containerMenu.forceHeldSlot(context.getHand());
                }
                return InteractionResult.PASS;
            }
            // Paper end
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, clickedPos, itemInHand);
            }

            itemInHand.shrink(1);
            level.setBlock(clickedPos, blockState1, Block.UPDATE_ALL_IMMEDIATE);
            level.gameEvent(GameEvent.BLOCK_CHANGE, clickedPos, GameEvent.Context.of(player, blockState1));
            level.levelEvent(player, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, clickedPos, 0);
            if (blockState.getBlock() instanceof ChestBlock && blockState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos connectedBlockPos = ChestBlock.getConnectedBlockPos(clickedPos, blockState);
                level.gameEvent(GameEvent.BLOCK_CHANGE, connectedBlockPos, GameEvent.Context.of(player, level.getBlockState(connectedBlockPos)));
                level.levelEvent(player, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, connectedBlockPos, 0);
            }

            return InteractionResult.SUCCESS;
        }).orElse(InteractionResult.PASS);
    }

    public static Optional<BlockState> getWaxed(BlockState state) {
        return Optional.ofNullable(WAXABLES.get().get(state.getBlock())).map(block -> block.withPropertiesOf(state));
    }

    @Override
    public boolean tryApplyToSign(Level level, SignBlockEntity sign, boolean isFront, Player player) {
        if (sign.setWaxed(true)) {
            level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, sign.getBlockPos(), 0);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canApplyToSign(SignText text, Player player) {
        return true;
    }
}
