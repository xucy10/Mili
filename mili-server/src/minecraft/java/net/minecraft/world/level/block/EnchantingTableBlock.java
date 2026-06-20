package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EnchantingTableBlock extends BaseEntityBlock {
    public static final MapCodec<EnchantingTableBlock> CODEC = simpleCodec(EnchantingTableBlock::new);
    public static final List<BlockPos> BOOKSHELF_OFFSETS = BlockPos.betweenClosedStream(-2, 0, -2, 2, 1, 2)
        .filter(blockPos -> Math.abs(blockPos.getX()) == 2 || Math.abs(blockPos.getZ()) == 2)
        .map(BlockPos::immutable)
        .toList();
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 12.0);

    @Override
    public MapCodec<EnchantingTableBlock> codec() {
        return CODEC;
    }

    protected EnchantingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static boolean isValidBookShelf(Level level, BlockPos enchantingTablePos, BlockPos bookshelfPos) {
        return level.getBlockState(enchantingTablePos.offset(bookshelfPos)).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
            && level.getBlockState(enchantingTablePos.offset(bookshelfPos.getX() / 2, bookshelfPos.getY(), bookshelfPos.getZ() / 2))
                .is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        for (BlockPos blockPos : BOOKSHELF_OFFSETS) {
            if (random.nextInt(16) == 0 && isValidBookShelf(level, pos, blockPos)) {
                level.addParticle(
                    ParticleTypes.ENCHANT,
                    pos.getX() + 0.5,
                    pos.getY() + 2.0,
                    pos.getZ() + 0.5,
                    blockPos.getX() + random.nextFloat() - 0.5,
                    blockPos.getY() - random.nextFloat() - 1.0F,
                    blockPos.getZ() + random.nextFloat() - 0.5
                );
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide()
            ? createTickerHelper(blockEntityType, BlockEntityType.ENCHANTING_TABLE, EnchantingTableBlockEntity::bookAnimationTick)
            : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity enchantingTableBlockEntity) {
            Component displayName = enchantingTableBlockEntity.getDisplayName();
            return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new EnchantmentMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos)),
                displayName
            );
        } else {
            return null;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
