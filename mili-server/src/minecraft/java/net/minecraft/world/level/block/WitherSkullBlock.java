package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import org.jspecify.annotations.Nullable;

public class WitherSkullBlock extends SkullBlock {
    public static final MapCodec<WitherSkullBlock> CODEC = simpleCodec(WitherSkullBlock::new);
    private static @Nullable BlockPattern witherPatternFull;
    private static @Nullable BlockPattern witherPatternBase;

    @Override
    public MapCodec<WitherSkullBlock> codec() {
        return CODEC;
    }

    protected WitherSkullBlock(BlockBehaviour.Properties properties) {
        super(SkullBlock.Types.WITHER_SKELETON, properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        checkSpawn(level, pos);
    }

    public static void checkSpawn(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SkullBlockEntity skullBlockEntity) {
            checkSpawn(level, pos, skullBlockEntity);
        }
    }

    public static void checkSpawn(Level level, BlockPos pos, SkullBlockEntity blockEntity) {
        if (level.getCurrentWorldData().captureBlockStates) return; // CraftBukkit // Folia - region threading
        if (!level.isClientSide()) {
            BlockState blockState = blockEntity.getBlockState();
            boolean flag = blockState.is(Blocks.WITHER_SKELETON_SKULL) || blockState.is(Blocks.WITHER_SKELETON_WALL_SKULL);
            if (flag && pos.getY() >= level.getMinY() && level.getDifficulty() != Difficulty.PEACEFUL) {
                BlockPattern.BlockPatternMatch blockPatternMatch = getOrCreateWitherFull().find(level, pos);
                if (blockPatternMatch != null) {
                    WitherBoss witherBoss = EntityType.WITHER.create(level, EntitySpawnReason.TRIGGERED);
                    if (witherBoss != null) {
                        // CarvedPumpkinBlock.clearPatternBlocks(level, blockPatternMatch); // CraftBukkit - move down
                        BlockPos pos1 = blockPatternMatch.getBlock(1, 2, 0).getPos();
                        witherBoss.snapTo(
                            pos1.getX() + 0.5,
                            pos1.getY() + 0.55,
                            pos1.getZ() + 0.5,
                            blockPatternMatch.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F,
                            0.0F
                        );
                        witherBoss.yBodyRot = blockPatternMatch.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
                        witherBoss.makeInvulnerable();
                        // CraftBukkit start
                        if (!level.addFreshEntity(witherBoss, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_WITHER)) {
                            return;
                        }
                        CarvedPumpkinBlock.clearPatternBlocks(level, blockPatternMatch); // CraftBukkit - from above
                        // CraftBukkit end

                        for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, witherBoss.getBoundingBox().inflate(50.0))) {
                            CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, witherBoss);
                        }

                        // level.addFreshEntity(witherBoss); // CraftBukkit - moved up
                        CarvedPumpkinBlock.updatePatternBlocks(level, blockPatternMatch);
                    }
                }
            }
        }
    }

    public static boolean canSpawnMob(Level level, BlockPos pos, ItemStack stack) {
        return stack.is(Items.WITHER_SKELETON_SKULL)
            && pos.getY() >= level.getMinY() + 2
            && level.getDifficulty() != Difficulty.PEACEFUL
            && !level.isClientSide()
            && getOrCreateWitherBase().find(level, pos) != null;
    }

    private static BlockPattern getOrCreateWitherFull() {
        if (witherPatternFull == null) {
            witherPatternFull = BlockPatternBuilder.start()
                .aisle("^^^", "###", "~#~")
                .where('#', block -> block.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS))
                .where(
                    '^',
                    BlockInWorld.hasState(
                        BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_SKULL).or(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_WALL_SKULL))
                    )
                )
                .where('~', block -> block.getState().isAir())
                .build();
        }

        return witherPatternFull;
    }

    private static BlockPattern getOrCreateWitherBase() {
        if (witherPatternBase == null) {
            witherPatternBase = BlockPatternBuilder.start()
                .aisle("   ", "###", "~#~")
                .where('#', block -> block.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS))
                .where('~', block -> block.getState().isAir())
                .build();
        }

        return witherPatternBase;
    }
}
