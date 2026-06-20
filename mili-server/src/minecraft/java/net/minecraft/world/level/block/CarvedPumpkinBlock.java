package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CarvedPumpkinBlock> CODEC = simpleCodec(CarvedPumpkinBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private @Nullable BlockPattern snowGolemBase;
    private @Nullable BlockPattern snowGolemFull;
    private @Nullable BlockPattern ironGolemBase;
    private @Nullable BlockPattern ironGolemFull;
    private @Nullable BlockPattern copperGolemBase;
    private @Nullable BlockPattern copperGolemFull;
    private static final Predicate<BlockState> PUMPKINS_PREDICATE = state -> state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN);

    @Override
    public MapCodec<? extends CarvedPumpkinBlock> codec() {
        return CODEC;
    }

    protected CarvedPumpkinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.trySpawnGolem(level, pos);
        }
    }

    public boolean canSpawnGolem(LevelReader level, BlockPos pos) {
        return this.getOrCreateSnowGolemBase().find(level, pos) != null
            || this.getOrCreateIronGolemBase().find(level, pos) != null
            || this.getOrCreateCopperGolemBase().find(level, pos) != null;
    }

    private void trySpawnGolem(Level level, BlockPos pos) {
        BlockPattern.BlockPatternMatch blockPatternMatch = this.getOrCreateSnowGolemFull().find(level, pos);
        if (blockPatternMatch != null) {
            SnowGolem snowGolem = EntityType.SNOW_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
            if (snowGolem != null) {
                spawnGolemInWorld(level, blockPatternMatch, snowGolem, blockPatternMatch.getBlock(0, 2, 0).getPos());
                return;
            }
        }

        BlockPattern.BlockPatternMatch blockPatternMatch1 = this.getOrCreateIronGolemFull().find(level, pos);
        if (blockPatternMatch1 != null) {
            IronGolem ironGolem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
            if (ironGolem != null) {
                ironGolem.setPlayerCreated(true);
                spawnGolemInWorld(level, blockPatternMatch1, ironGolem, blockPatternMatch1.getBlock(1, 2, 0).getPos());
                return;
            }
        }

        BlockPattern.BlockPatternMatch blockPatternMatch2 = this.getOrCreateCopperGolemFull().find(level, pos);
        if (blockPatternMatch2 != null) {
            CopperGolem copperGolem = EntityType.COPPER_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
            if (copperGolem != null) {
                spawnGolemInWorld(level, blockPatternMatch2, copperGolem, blockPatternMatch2.getBlock(0, 0, 0).getPos());
                if (!copperGolem.valid) return; // Paper - entityspawnevent - entity was not added to the world so prevent world mutation
                this.replaceCopperBlockWithChest(level, blockPatternMatch2);
                copperGolem.spawn(this.getWeatherStateFromPattern(blockPatternMatch2));
            }
        }
    }

    private WeatheringCopper.WeatherState getWeatherStateFromPattern(BlockPattern.BlockPatternMatch patternMatch) {
        BlockState state = patternMatch.getBlock(0, 1, 0).getState();
        return state.getBlock() instanceof WeatheringCopper weatheringCopper
            ? weatheringCopper.getAge()
            : Optional.ofNullable(HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock()))
                .filter(block1 -> block1 instanceof WeatheringCopper)
                .map(block1 -> (WeatheringCopper)block1)
                .orElse((WeatheringCopper)Blocks.COPPER_BLOCK)
                .getAge();
    }

    private static void spawnGolemInWorld(Level level, BlockPattern.BlockPatternMatch patternMatch, Entity golem, BlockPos pos) {
        // clearPatternBlocks(level, patternMatch); // Paper - moved down
        golem.snapTo(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5, 0.0F, 0.0F);
        // Paper start
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason;
        if (golem.getType() == net.minecraft.world.entity.EntityType.SNOW_GOLEM) {
            spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN;
        } else if (golem.getType() == net.minecraft.world.entity.EntityType.COPPER_GOLEM) {
            spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM;
        } else {
            spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM;
        }
        if (!level.addFreshEntity(golem, spawnReason)) {
            return;
        }
        // Paper end
        clearPatternBlocks(level, patternMatch); // Paper - from above

        for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, golem);
        }

        updatePatternBlocks(level, patternMatch);
    }

    public static void clearPatternBlocks(Level level, BlockPattern.BlockPatternMatch patternMatch) {
        for (int i = 0; i < patternMatch.getWidth(); i++) {
            for (int i1 = 0; i1 < patternMatch.getHeight(); i1++) {
                BlockInWorld block = patternMatch.getBlock(i, i1, 0);
                level.setBlock(block.getPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, block.getPos(), Block.getId(block.getState()));
            }
        }
    }

    public static void updatePatternBlocks(Level level, BlockPattern.BlockPatternMatch patternMatch) {
        for (int i = 0; i < patternMatch.getWidth(); i++) {
            for (int i1 = 0; i1 < patternMatch.getHeight(); i1++) {
                BlockInWorld block = patternMatch.getBlock(i, i1, 0);
                level.updateNeighborsAt(block.getPos(), Blocks.AIR);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private BlockPattern getOrCreateSnowGolemBase() {
        if (this.snowGolemBase == null) {
            this.snowGolemBase = BlockPatternBuilder.start()
                .aisle(" ", "#", "#")
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK)))
                .build();
        }

        return this.snowGolemBase;
    }

    private BlockPattern getOrCreateSnowGolemFull() {
        if (this.snowGolemFull == null) {
            this.snowGolemFull = BlockPatternBuilder.start()
                .aisle("^", "#", "#")
                .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK)))
                .build();
        }

        return this.snowGolemFull;
    }

    private BlockPattern getOrCreateIronGolemBase() {
        if (this.ironGolemBase == null) {
            this.ironGolemBase = BlockPatternBuilder.start()
                .aisle("~ ~", "###", "~#~")
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
                .where('~', BlockInWorld.hasState(BlockBehaviour.BlockStateBase::isAir))
                .build();
        }

        return this.ironGolemBase;
    }

    private BlockPattern getOrCreateIronGolemFull() {
        if (this.ironGolemFull == null) {
            this.ironGolemFull = BlockPatternBuilder.start()
                .aisle("~^~", "###", "~#~")
                .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
                .where('~', BlockInWorld.hasState(BlockBehaviour.BlockStateBase::isAir))
                .build();
        }

        return this.ironGolemFull;
    }

    private BlockPattern getOrCreateCopperGolemBase() {
        if (this.copperGolemBase == null) {
            this.copperGolemBase = BlockPatternBuilder.start().aisle(" ", "#").where('#', BlockInWorld.hasState(state -> state.is(BlockTags.COPPER))).build();
        }

        return this.copperGolemBase;
    }

    private BlockPattern getOrCreateCopperGolemFull() {
        if (this.copperGolemFull == null) {
            this.copperGolemFull = BlockPatternBuilder.start()
                .aisle("^", "#")
                .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
                .where('#', BlockInWorld.hasState(state -> state.is(BlockTags.COPPER)))
                .build();
        }

        return this.copperGolemFull;
    }

    public void replaceCopperBlockWithChest(Level level, BlockPattern.BlockPatternMatch patternMatch) {
        BlockInWorld block = patternMatch.getBlock(0, 1, 0);
        BlockInWorld block1 = patternMatch.getBlock(0, 0, 0);
        Direction direction = block1.getState().getValue(FACING);
        BlockState fromCopperBlock = CopperChestBlock.getFromCopperBlock(block.getState().getBlock(), direction, level, block.getPos());
        level.setBlock(block.getPos(), fromCopperBlock, Block.UPDATE_CLIENTS);
    }
}
