package net.minecraft.world.item;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public class StandingAndWallBlockItem extends BlockItem {
    public final Block wallBlock;
    private final Direction attachmentDirection;

    public StandingAndWallBlockItem(Block block, Block wallBlock, Direction attachmentDirection, Item.Properties properties) {
        super(block, properties);
        this.wallBlock = wallBlock;
        this.attachmentDirection = attachmentDirection;
    }

    protected boolean canPlace(LevelReader level, BlockState state, BlockPos pos) {
        return state.canSurvive(level, pos);
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState stateForPlacement = this.wallBlock.getStateForPlacement(context);
        BlockState blockState = null;
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction != this.attachmentDirection.getOpposite()) {
                BlockState blockState1 = direction == this.attachmentDirection ? this.getBlock().getStateForPlacement(context) : stateForPlacement;
                if (blockState1 != null && this.canPlace(level, blockState1, clickedPos)) {
                    blockState = blockState1;
                    break;
                }
            }
        }

        // return blockState != null && level.isUnobstructed(blockState, clickedPos, CollisionContext.empty()) ? blockState : null;
        // CraftBukkit start
        if (blockState != null) {
            boolean defaultReturn = level.isUnobstructed(blockState, clickedPos, CollisionContext.empty());
            org.bukkit.entity.Player player = (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) ? serverPlayer.getBukkitEntity() : null;

            org.bukkit.event.block.BlockCanBuildEvent event = new org.bukkit.event.block.BlockCanBuildEvent(org.bukkit.craftbukkit.block.CraftBlock.at(context.getLevel(), clickedPos), player, org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(blockState), defaultReturn, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand())); // Paper - Expose hand in BlockCanBuildEvent
            context.getLevel().getCraftServer().getPluginManager().callEvent(event);

            return (event.isBuildable()) ? blockState : null;
        } else {
            return null;
        }
        // CraftBukkit end
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        super.registerBlocks(blockToItemMap, item);
        blockToItemMap.put(this.wallBlock, item);
    }
}
