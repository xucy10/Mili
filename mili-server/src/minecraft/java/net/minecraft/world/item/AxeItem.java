package net.minecraft.world.item;

import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

public class AxeItem extends Item {
    protected static final Map<Block, Block> STRIPPABLES = new Builder<Block, Block>()
        .put(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD)
        .put(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG)
        .put(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD)
        .put(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG)
        .put(Blocks.PALE_OAK_WOOD, Blocks.STRIPPED_PALE_OAK_WOOD)
        .put(Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG)
        .put(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD)
        .put(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG)
        .put(Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD)
        .put(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG)
        .put(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD)
        .put(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG)
        .put(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD)
        .put(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG)
        .put(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD)
        .put(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG)
        .put(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM)
        .put(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE)
        .put(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM)
        .put(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE)
        .put(Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_WOOD)
        .put(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG)
        .put(Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK)
        .build();

    public AxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {
        super(properties.axe(material, attackDamage, attackSpeed));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (playerHasBlockingItemUseIntent(context)) {
            return InteractionResult.PASS;
        } else {
            Optional<BlockState> optional = this.evaluateNewBlockState(level, clickedPos, player, level.getBlockState(clickedPos));
            if (optional.isEmpty()) {
                return InteractionResult.PASS;
            } else {
                ItemStack itemInHand = context.getItemInHand();
                // Paper start - EntityChangeBlockEvent
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, clickedPos, optional.get())) {
                    return InteractionResult.PASS;
                }
                // Paper end
                if (player instanceof ServerPlayer) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer)player, clickedPos, itemInHand);
                }

                level.setBlock(clickedPos, optional.get(), Block.UPDATE_ALL_IMMEDIATE);
                level.gameEvent(GameEvent.BLOCK_CHANGE, clickedPos, GameEvent.Context.of(player, optional.get()));
                if (player != null) {
                    itemInHand.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                }

                return InteractionResult.SUCCESS;
            }
        }
    }

    private static boolean playerHasBlockingItemUseIntent(UseOnContext context) {
        Player player = context.getPlayer();
        return context.getHand().equals(InteractionHand.MAIN_HAND)
            && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)
            && !player.isSecondaryUseActive();
    }

    private Optional<BlockState> evaluateNewBlockState(Level level, BlockPos pos, @Nullable Player player, BlockState state) {
        Optional<BlockState> stripped = this.getStripped(state);
        if (stripped.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return stripped;
        } else {
            Optional<BlockState> previous = WeatheringCopper.getPrevious(state);
            if (previous.isPresent()) {
                spawnSoundAndParticle(level, pos, player, state, SoundEvents.AXE_SCRAPE, LevelEvent.PARTICLES_SCRAPE);
                return previous;
            } else {
                Optional<BlockState> optional = Optional.ofNullable(HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock()))
                    .map(block -> block.withPropertiesOf(state));
                if (optional.isPresent()) {
                    spawnSoundAndParticle(level, pos, player, state, SoundEvents.AXE_WAX_OFF, LevelEvent.PARTICLES_WAX_OFF);
                    return optional;
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    private static void spawnSoundAndParticle(Level level, BlockPos pos, @Nullable Player player, BlockState state, SoundEvent sound, int levelEvent) {
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.levelEvent(player, levelEvent, pos, 0);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connectedBlockPos = ChestBlock.getConnectedBlockPos(pos, state);
            level.gameEvent(GameEvent.BLOCK_CHANGE, connectedBlockPos, GameEvent.Context.of(player, level.getBlockState(connectedBlockPos)));
            level.levelEvent(player, levelEvent, connectedBlockPos, 0);
        }
    }

    private Optional<BlockState> getStripped(BlockState unstrippedState) {
        return Optional.ofNullable(STRIPPABLES.get(unstrippedState.getBlock()))
            .map(block -> block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, unstrippedState.getValue(RotatedPillarBlock.AXIS)));
    }
}
