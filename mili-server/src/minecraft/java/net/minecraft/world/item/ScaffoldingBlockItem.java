package net.minecraft.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class ScaffoldingBlockItem extends BlockItem {
    public ScaffoldingBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        BlockState blockState = level.getBlockState(clickedPos);
        Block block = this.getBlock();
        if (!blockState.is(block)) {
            return ScaffoldingBlock.getDistance(level, clickedPos) == 7 ? null : context;
        } else {
            Direction direction;
            if (context.isSecondaryUseActive()) {
                direction = context.isInside() ? context.getClickedFace().getOpposite() : context.getClickedFace();
            } else {
                direction = context.getClickedFace() == Direction.UP ? context.getHorizontalDirection() : Direction.UP;
            }

            int i = 0;
            BlockPos.MutableBlockPos mutableBlockPos = clickedPos.mutable().move(direction);

            while (i < 7) {
                if (!level.isClientSide() && !level.isInWorldBounds(mutableBlockPos)) {
                    Player player = context.getPlayer();
                    int maxY = level.getMaxY();
                    if (player instanceof ServerPlayer && mutableBlockPos.getY() > maxY) {
                        ((ServerPlayer)player).sendSystemMessage(Component.translatable("build.tooHigh", maxY).withStyle(ChatFormatting.RED), true);
                    }
                    break;
                }

                blockState = level.getBlockState(mutableBlockPos);
                if (!blockState.is(this.getBlock())) {
                    if (blockState.canBeReplaced(context)) {
                        return BlockPlaceContext.at(context, mutableBlockPos, direction);
                    }
                    break;
                }

                mutableBlockPos.move(direction);
                if (direction.getAxis().isHorizontal()) {
                    i++;
                }
            }

            return null;
        }
    }

    @Override
    protected boolean mustSurvive() {
        return false;
    }
}
