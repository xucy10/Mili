package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class StructureBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<StructureBlock> CODEC = simpleCodec(StructureBlock::new);
    public static final EnumProperty<StructureMode> MODE = BlockStateProperties.STRUCTUREBLOCK_MODE;

    @Override
    public MapCodec<StructureBlock> codec() {
        return CODEC;
    }

    protected StructureBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(MODE, StructureMode.LOAD));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof StructureBlockEntity) {
            return (InteractionResult)(((StructureBlockEntity)blockEntity).usedBy(player) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            if (placer != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof StructureBlockEntity) {
                    ((StructureBlockEntity)blockEntity).createdBy(placer);
                }
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel) {
            if (level.getBlockEntity(pos) instanceof StructureBlockEntity structureBlockEntity) {
                boolean hasNeighborSignal = level.hasNeighborSignal(pos);
                boolean isPowered = structureBlockEntity.isPowered();
                if (hasNeighborSignal && !isPowered) {
                    structureBlockEntity.setPowered(true);
                    this.trigger((ServerLevel)level, structureBlockEntity);
                } else if (!hasNeighborSignal && isPowered) {
                    structureBlockEntity.setPowered(false);
                }
            }
        }
    }

    private void trigger(ServerLevel level, StructureBlockEntity blockEntity) {
        switch (blockEntity.getMode()) {
            case SAVE:
                blockEntity.saveStructure(false);
                break;
            case LOAD:
                blockEntity.placeStructure(level);
                break;
            case CORNER:
                blockEntity.unloadStructure();
            case DATA:
        }
    }
}
